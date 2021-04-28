package dev.neeffect.nee.ctx.web

import dev.neeffect.nee.ANee
import dev.neeffect.nee.ctx.web.util.RenderHelper
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.async.AsyncEnvWrapper
import dev.neeffect.nee.effects.async.AsyncSupport
import dev.neeffect.nee.effects.async.ExecutionContextProvider
import dev.neeffect.nee.effects.monitoring.TraceProvider
import dev.neeffect.nee.effects.monitoring.TraceResource
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.effects.tx.TxConnection
import dev.neeffect.nee.effects.tx.TxProvider
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import io.ktor.application.ApplicationCall

data class WebContext<R, G : TxProvider<R, G>>(
    private val jdbcProvider: TxProvider<R, G>,
    private val securityProvider: SecurityProvider<User, UserRole>,
    private val executionContextProvider: ExecutionContextProvider,
    private val errorHandler: ErrorHandler = DefaultErrorHandler,
    private val contextProvider: WebContextProvider<R, G>,
    private val traceProvider: TraceProvider<*>,
    private val timeProvider: TimeProvider,
    private val applicationCall: ApplicationCall,
    private val asyncEnv: AsyncEnvWrapper<WebContext<R, G>> = AsyncEnvWrapper()
) : TxProvider<R, WebContext<R, G>>,
    SecurityProvider<User, UserRole> by securityProvider,
    ExecutionContextProvider by executionContextProvider,
    TraceProvider<WebContext<R, G>>,
    TimeProvider by timeProvider,
    Logging,
    AsyncSupport<WebContext<R, G>> by asyncEnv {

    private val renderHelper = RenderHelper(contextProvider.jacksonMapper(), errorHandler)

    override fun getTrace(): TraceResource = traceProvider.getTrace()

    override fun setTrace(newState: TraceResource): WebContext<R, G> =
        this.copy(traceProvider = traceProvider.setTrace(newState))

    override fun getConnection(): TxConnection<R> = jdbcProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<R>) =
        this.copy(jdbcProvider = jdbcProvider.setConnectionState(newState))

    suspend fun serveText(businessFunction: ANee<WebContext<R, G>, String>) =
        businessFunction.perform(this).let { result ->
            renderHelper.serveText(applicationCall, result)
        }

    suspend fun <E, A> serveMessage(msg: Out<E, A>): Unit =
        renderHelper.serveMessage(applicationCall, msg)

    suspend fun serveMessage(businessFunction: ANee<WebContext<R, G>, Any>) =
        serveMessage(businessFunction.perform(this))
}
