package pl.setblack.nee.ctx.web

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.response.respond
import io.vavr.collection.List
import io.vavr.jackson.datatype.VavrModule
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import pl.setblack.nee.ANee
import pl.setblack.nee.anyError
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.async.AsyncEffect
import pl.setblack.nee.effects.async.ExecutionContextProvider
import pl.setblack.nee.effects.cache.CacheEffect
import pl.setblack.nee.effects.cache.caffeine.CaffeineProvider
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.effects.security.SecuredRunEffect
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxEffect
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.effects.utils.Logging
import pl.setblack.nee.effects.utils.logger
import pl.setblack.nee.effects.utils.merge
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRole
import java.sql.Connection


class WebContext<R, G : TxProvider<R, G>>(
    private val jdbcProvider: TxProvider<R, G>,
    private val securityProvider: SecurityProvider<User, UserRole>,
    private val executionContextProvider: ExecutionContextProvider,
    private val errorHandler: ErrorHandler = DefaultErrorHandler,
    private val applicationCall: ApplicationCall
) : TxProvider<R, WebContext<R,G>>,
    SecurityProvider<User, UserRole> by securityProvider,
    ExecutionContextProvider by executionContextProvider,
    Logging {
    override fun getConnection(): TxConnection<R> = jdbcProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<R>) =
        WebContext(
            jdbcProvider.setConnectionState(newState),
            securityProvider,
            executionContextProvider,
            errorHandler,
            applicationCall
        )

    fun <P> serveText(businessFunction: ANee<WebContext<R,G>, P, String>, param: P) =
        businessFunction.perform(this)(param)
            .onComplete { outcome ->
                val message = outcome.bimap<OutgoingContent,OutgoingContent>(::serveError, { regularResult ->
                    TextContent(
                        text = regularResult,
                        contentType = ContentType.Text.Plain,
                        status = HttpStatusCode.OK
                    )
                }).merge()
                runBlocking { applicationCall.respond(message) }
            }

    suspend fun <E,A> serveMessage(msg : Out<E, A>) : Unit =
        msg.toFuture().toCompletableFuture().await().let { outcome ->
            val message = outcome.bimap<OutgoingContent,OutgoingContent>({ serveError(it as Any) }, { regularResult ->
                val bytes = jacksonMapper.writeValueAsBytes(regularResult)
                ByteArrayContent(
                    bytes = bytes,
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }).merge()
            try {
                applicationCall.respond(message)
            } catch (e: Exception) {
                logger().warn("exception in sending response", e)
            }
        }

    suspend fun <P> serveMessage(businessFunction: ANee<WebContext<R,G>, P, Any>, param: P) =
        serveMessage(businessFunction.perform(this)(param))

    private fun serveError(errorResult: Any): OutgoingContent = errorHandler(errorResult)


    companion object {


        internal val jacksonMapper = ObjectMapper()
            .registerModule(VavrModule())

    }

}


