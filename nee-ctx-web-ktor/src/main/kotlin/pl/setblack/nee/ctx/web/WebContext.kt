package pl.setblack.nee.ctx.web

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.request.header
import io.ktor.response.respond
import io.vavr.collection.List
import io.vavr.jackson.datatype.VavrModule
import io.vavr.kotlin.option
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import pl.setblack.nee.ANee
import pl.setblack.nee.Logging
import pl.setblack.nee.anyError
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.async.AsyncEffect
import pl.setblack.nee.effects.async.ECProvider
import pl.setblack.nee.effects.async.ExecutionContextProvider
import pl.setblack.nee.effects.async.ExecutorExecutionContext
import pl.setblack.nee.effects.cache.CacheEffect
import pl.setblack.nee.effects.cache.caffeine.CaffeineProvider
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.effects.security.SecuredRunEffect
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxEffect
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.logger
import pl.setblack.nee.merge
import pl.setblack.nee.security.DBUserRealm
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRole
import java.sql.Connection
import java.util.concurrent.Executors


class WebContext(
    private val jdbcProvider: TxProvider<Connection, JDBCProvider>,
    private val securityProvider: SecurityProvider<User, UserRole>,
    private val executionContextProvider: ExecutionContextProvider,
    private val applicationCall: ApplicationCall
) : TxProvider<Connection, WebContext>,
    SecurityProvider<User, UserRole> by securityProvider,
    ExecutionContextProvider by executionContextProvider,
    Logging {
    override fun getConnection(): TxConnection<Connection> = jdbcProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<Connection>): WebContext =
        WebContext(
            jdbcProvider.setConnectionState(newState),
            securityProvider,
            executionContextProvider,
            applicationCall
        )

    fun <P> serveText(businessFunction: ANee<WebContext, P, String>, param: P) =
        businessFunction.perform(this)(param)
            .onComplete { outcome ->
                val message = outcome.bimap(::serveError, { regularResult ->
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
            val message = outcome.bimap({ serveError(it as Any) as OutgoingContent }, { regularResult ->
                val bytes = jacksonMapper.writeValueAsBytes(regularResult)
                ByteArrayContent(
                    bytes = bytes,
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                ) as OutgoingContent
            }).merge()
            try {
                applicationCall.respond(message)
            } catch (e: Exception) {
                logger().warn("exception in sending response", e)
            }
        }

    suspend fun <P> serveMessage(businessFunction: ANee<WebContext, P, Any>, param: P) =
        serveMessage(businessFunction.perform(this)(param))

    private fun serveError(errorResult: Any): TextContent =
        TextContent(
            text = "error:" + errorResult.toString(),
            contentType = ContentType.Text.Plain,
            status = HttpStatusCode.InternalServerError
        )

    companion object {
        private val jdbcTasksScheduler = Executors.newFixedThreadPool(4)

        private val jdbcExecutionContextProvider =
            ECProvider(ExecutorExecutionContext(jdbcTasksScheduler))

        fun create(jdbc: JDBCConfig, call: ApplicationCall): WebContext =
            JDBCProvider(jdbc).let { jdbcProvider ->
                val dbUserRealm = DBUserRealm(jdbcProvider)
                val authProvider = BasicAuthProvider<User, UserRole>(
                    call.request.header("Authorization").option(),
                    dbUserRealm
                )
                WebContext(
                    jdbcProvider,
                    authProvider,
                    jdbcExecutionContextProvider,
                    call
                )
            }

        internal val jacksonMapper = ObjectMapper()
            .registerModule(VavrModule())

    }

    object Effects {
        val async = AsyncEffect<WebContext>()
        fun secured(roles: List<UserRole>) = SecuredRunEffect<User, UserRole, WebContext>(roles)
        val jdbc = TxEffect<Connection, WebContext>().anyError()
        val cache = CacheEffect<WebContext, Nothing>(CaffeineProvider()).anyError()

        //TODO think about securing async jdbc -
        // notify in async that some resources are potentially used - and should not be closed

    }
}


