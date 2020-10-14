package pl.setblack.nee.ctx.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.vavr.collection.List
import io.vavr.jackson.datatype.VavrModule
import io.vavr.kotlin.option
import io.vavr.kotlin.toVavrList
import pl.setblack.nee.Nee
import pl.setblack.nee.andThen
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
import pl.setblack.nee.effects.monitoring.Logger
import pl.setblack.nee.effects.monitoring.MutableInMemLogger
import pl.setblack.nee.effects.monitoring.SimpleBufferedLogger
import pl.setblack.nee.effects.monitoring.SimpleTraceProvider
import pl.setblack.nee.effects.monitoring.TraceEffect
import pl.setblack.nee.effects.monitoring.TraceProvider
import pl.setblack.nee.effects.monitoring.TraceResource
import pl.setblack.nee.effects.security.SecuredRunEffect
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.effects.tx.TxEffect
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.security.DBUserRealm
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRealm
import pl.setblack.nee.security.UserRole
import java.sql.Connection
import java.util.concurrent.Executors

class EffectsInstance<R, G : TxProvider<R, G>> {
    val trace = TraceEffect<WebContext<R,G>>("web")

    val async = trace.andThen(AsyncEffect<WebContext<R, G>>())
        .anyError()
    fun secured(roles: List<UserRole>) =
        trace.andThen(SecuredRunEffect<User, UserRole, WebContext<R, G>>(roles)).anyError()
    val jdbc =
        trace.andThen(TxEffect<Connection, WebContext<R, G>>()).anyError()
    val cache = CacheEffect<WebContext<R, G>, Nothing>(CaffeineProvider()).anyError()

}

interface WebContextProvider<R, G : TxProvider<R, G>> {
    fun create(call: ApplicationCall): WebContext<R, G>

    fun effects(): EffectsInstance<R, G>

    fun sysApi() : Route.() -> Unit = {
        route("/sys") {
            healthCheck()()
            userSecurityApi()()
            monitoringApi()()
        }
    }

    fun healthCheck(): Route.() -> Unit = {
        get("healthCheck") {
            call.respond(HttpStatusCode.OK, "ok")
        }
    }

    fun userSecurityApi(): Route.() -> Unit = {
        get("currentUser") {
            val f = Nee.constP(effects().secured(List.empty())){ctx->
                ctx.getSecurityContext().flatMap { secCtx -> secCtx.getCurrentUser()}
            }.anyError()
            val z  = Nee.flatOut(f)
            create(call).serveMessage(z, Unit)
        }
        get("hasRoles") {
            val roles = (call.request.queryParameters["roles"] ?:"").split(",")
                .toVavrList().map { UserRole(it)}

            val f =
                Nee.constP(effects().secured(roles)){
                "ok"
            }.anyError()
            create(call).serveMessage(f, Unit)
        }
    }

    open fun monitoringApi(): Route.() -> Unit = {

    }

    abstract fun jacksonMapper() : ObjectMapper

}

abstract class BaseWebContext<R, G : TxProvider<R, G>> : WebContextProvider<R, G> {

    private val effectsInstance = EffectsInstance<R, G>()

    override fun effects(): EffectsInstance<R, G> = effectsInstance

    abstract val txProvider: TxProvider<R, G>

    abstract fun authProvider(call: ApplicationCall): SecurityProvider<User, UserRole>

    open val errorHandler: ErrorHandler by lazy { DefaultErrorHandler }

    abstract val executionContextProvider: ExecutionContextProvider


        override fun create(call: ApplicationCall) = WebContext(
            txProvider,
            authProvider(call),
            executionContextProvider,
            errorHandler,
            this,
            traceProvider,
            call
        )

    override fun jacksonMapper(): ObjectMapper = jacksonMapper

    open val logger by lazy {
        MutableInMemLogger()
    }

    open val traceResource : TraceResource by lazy {
        TraceResource("web",
            logger)
    }

    open  val traceProvider: TraceProvider<*> by lazy {
        SimpleTraceProvider(traceResource)
    }

    open val jacksonMapper by lazy {
        ObjectMapper()
            .registerModule(VavrModule())
            .registerModule(KotlinModule())
    }

    override fun monitoringApi(): Route.() -> Unit = {
        get("logs") {
            val bytes = jacksonMapper().writeValueAsBytes(logger.getLogs())
            call.respond(ByteArrayContent(
                bytes = bytes,
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            ))
        }
        get("report") {
            val bytes = jacksonMapper().writeValueAsBytes(logger.getReport())
            call.respond(ByteArrayContent(
                bytes = bytes,
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            ))
        }
    }
}

abstract class JDBCBasedWebContext :
    BaseWebContext<Connection, JDBCProvider>() {

    open val jdbcTasksScheduler = Executors.newFixedThreadPool(4)

    override val executionContextProvider =
        ECProvider(ExecutorExecutionContext(jdbcTasksScheduler))

    abstract val jdbcProvider: JDBCProvider

    open val userRealm: UserRealm<User, UserRole> by lazy {
        DBUserRealm(jdbcProvider)
    }

    override fun authProvider(call: ApplicationCall): SecurityProvider<User, UserRole> =
        BasicAuthProvider<User, UserRole>(
            call.request.header("Authorization").option(),
            userRealm
        )

    override val txProvider: TxProvider<Connection, JDBCProvider> by lazy {
        jdbcProvider
    }
}
