package dev.neeffect.nee.ctx.web

import dev.neeffect.nee.ANee
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.async.ExecutionContextProvider
import dev.neeffect.nee.effects.monitoring.TraceProvider
import dev.neeffect.nee.effects.monitoring.TraceResource
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.effects.tx.TxConnection
import dev.neeffect.nee.effects.tx.TxProvider
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.effects.utils.logger
import dev.neeffect.nee.effects.utils.merge
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking


data class WebContext<R, G : TxProvider<R, G>>(
    private val jdbcProvider: TxProvider<R, G>,
    private val securityProvider: SecurityProvider<User, UserRole>,
    private val executionContextProvider: ExecutionContextProvider,
    private val errorHandler: ErrorHandler = DefaultErrorHandler,
    private val contextProvider: WebContextProvider<R,G>,
    private val traceProvider: TraceProvider<*>,
    private val timeProvider: TimeProvider,
    private val applicationCall: ApplicationCall
) : TxProvider<R, WebContext<R,G>>,
    SecurityProvider<User, UserRole> by securityProvider,
    ExecutionContextProvider by executionContextProvider,
    TraceProvider<WebContext<R,G>>,
    TimeProvider by timeProvider,
    Logging {
    override fun getTrace(): TraceResource = traceProvider.getTrace()


    override fun setTrace(newState: TraceResource): WebContext<R, G> =
       this.copy(traceProvider = traceProvider.setTrace(newState))


    override fun getConnection(): TxConnection<R> = jdbcProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<R>) =
        this.copy( jdbcProvider = jdbcProvider.setConnectionState(newState))


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
                runBlocking { applicationCall.respond(message)}
            }

    suspend fun <E,A> serveMessage(msg : Out<E, A>) : Unit =
        msg.toFuture().toCompletableFuture().await().let { outcome ->
            val message = outcome.bimap<OutgoingContent,OutgoingContent>({ serveError(it as Any) }, { regularResult ->
                val bytes = contextProvider.jacksonMapper().writeValueAsBytes(regularResult)
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

}


