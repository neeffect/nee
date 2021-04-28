package dev.neeffect.nee.ctx.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.flatMap
import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.ctx.web.pure.RouteBuilder
import dev.neeffect.nee.effects.async.AsyncEffect
import dev.neeffect.nee.effects.async.ECProvider
import dev.neeffect.nee.effects.async.ExecutionContextProvider
import dev.neeffect.nee.effects.async.ExecutorExecutionContext
import dev.neeffect.nee.effects.async.ThreadedExecutionContextProvider
import dev.neeffect.nee.effects.cache.CacheEffect
import dev.neeffect.nee.effects.cache.caffeine.CaffeineProvider
import dev.neeffect.nee.effects.jdbc.JDBCProvider
import dev.neeffect.nee.effects.monitoring.CodeNameFinder
import dev.neeffect.nee.effects.monitoring.MutableInMemLogger
import dev.neeffect.nee.effects.monitoring.SimpleTraceProvider
import dev.neeffect.nee.effects.monitoring.TraceEffect
import dev.neeffect.nee.effects.monitoring.TraceProvider
import dev.neeffect.nee.effects.monitoring.TraceResource
import dev.neeffect.nee.effects.security.DummySecurityProvider
import dev.neeffect.nee.effects.security.SecuredRunEffect
import dev.neeffect.nee.effects.security.SecurityError
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.effects.tx.DummyTxProvider
import dev.neeffect.nee.effects.tx.TxEffect
import dev.neeffect.nee.effects.tx.TxError
import dev.neeffect.nee.effects.tx.TxProvider
import dev.neeffect.nee.security.DBUserRealm
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRealm
import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.with
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
import java.sql.Connection
import java.util.concurrent.Executors

class EffectsInstance<R, G : TxProvider<R, G>> {

    val plainFx = PlainInstances<R, G>()

    val trace: TraceEffect<WebContext<R, G>> = TraceEffect<WebContext<R, G>>("web")

    val async: Effect<WebContext<R, G>, Nothing> = trace.with(AsyncEffect<WebContext<R, G>>())

    val nop: Effect<WebContext<R, G>, Nothing> = NoEffect.get()

    fun secured(roles: List<UserRole>): Effect<WebContext<R, G>, SecurityError> =
        trace.with(SecuredRunEffect<User, UserRole, WebContext<R, G>>(roles))

    val tx: Effect<WebContext<R, G>, TxError> =
        trace.with(async).with(plainFx.tx)

    fun cache() = Cache<R, G>()

    class PlainInstances<R, G : TxProvider<R, G>> {
        val tx = TxEffect<Connection, WebContext<R, G>>()
    }

    class Cache<R, G : TxProvider<R, G>> {
        val internalCache = CaffeineProvider()
        fun <P> of(p: P): CacheEffect<WebContext<R, G>, Nothing, P> =
            CacheEffect<WebContext<R, G>, Nothing, P>(p, internalCache)
    }
}

interface WebContextProvider<R, G : TxProvider<R, G>> {
    fun create(call: ApplicationCall): WebContext<R, G>

    fun fx(): EffectsInstance<R, G>

    fun sysApi(): Route.() -> Unit = {
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
            val f = Nee.with(fx().secured(List.empty())) { ctx ->
                ctx.getSecurityContext().flatMap { secCtx -> secCtx.getCurrentUser() }
            }.anyError()
            val z = Nee.flatOut(f)
            create(call).serveMessage(z)
        }
        get("hasRoles") {
            val roles = (call.request.queryParameters["roles"] ?: "").split(",")
                .toVavrList().map { UserRole(it) }

            val f =
                Nee.with(fx().secured(roles)) {
                    "ok"
                }.anyError()
            create(call).serveMessage(f)
        }
    }

    open fun monitoringApi(): Route.() -> Unit = {
    }

    fun jacksonMapper(): ObjectMapper

    fun <E, A> async(func: () -> Nee<WebContext<R, G>, E, A>): Nee<WebContext<R, G>, Any, A> =
        CodeNameFinder.guessCodePlaceName(2).let { whereItIsDefined ->
            val z: Nee<WebContext<R, G>, Nothing, Nee<WebContext<R, G>, E, A>> = Nee.with(this.fx().async) { r ->
                r.getTrace().putNamedPlace(whereItIsDefined)
                func()
            }
            z.anyError().flatMap { it.anyError() }
        }

    fun routeBuilder() = RouteBuilder<R,G>()
}

abstract class BaseWebContextProvider<R, G : TxProvider<R, G>> : WebContextProvider<R, G> {

    private val effectsInstance = EffectsInstance<R, G>()

    override fun fx(): EffectsInstance<R, G> = effectsInstance

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
        timeProvider,
        call
    )

    override fun jacksonMapper(): ObjectMapper = jacksonMapper

    open val logger by lazy {
        MutableInMemLogger()
    }

    open val timeProvider: TimeProvider by lazy {
        HasteTimeProvider()
    }

    open val traceResource: TraceResource by lazy {
        TraceResource(
            "web",
            logger
        )
    }

    open val traceProvider: TraceProvider<*> by lazy {
        SimpleTraceProvider(traceResource)
    }

    open val jacksonMapper by lazy {
        DefaultJacksonMapper.mapper
    }

    override fun monitoringApi(): Route.() -> Unit = {
        get("logs") {
            val bytes = jacksonMapper().writeValueAsBytes(logger.getLogs())
            call.respond(
                ByteArrayContent(
                    bytes = bytes,
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            )
        }
        get("report") {
            val bytes = jacksonMapper().writeValueAsBytes(logger.getReport())
            call.respond(
                ByteArrayContent(
                    bytes = bytes,
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            )
        }
    }

    companion object {
        fun createTransient(customErrorHandler: ErrorHandler = DefaultErrorHandler) =
            object : BaseWebContextProvider<Nothing, DummyTxProvider>() {
                val ec = ThreadedExecutionContextProvider()

                val security = DummySecurityProvider<User, UserRole>()

                override val txProvider: TxProvider<Nothing, DummyTxProvider> = DummyTxProvider

                override fun authProvider(call: ApplicationCall): SecurityProvider<User, UserRole> = security

                override val executionContextProvider: ExecutionContextProvider = ec

                override val errorHandler: ErrorHandler = customErrorHandler
            }
    }
}

abstract class JDBCBasedWebContextProvider :
    BaseWebContextProvider<Connection, JDBCProvider>() {

    open val jdbcTasksScheduler = Executors.newFixedThreadPool(defaultThreadPool)

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

    companion object {
        const val defaultThreadPool = 4
    }
}

object DefaultJacksonMapper {
    val mapper = ObjectMapper()
        .registerModule(VavrModule())
        .registerModule(KotlinModule())
        .registerModule(JavaTimeModule())
}
