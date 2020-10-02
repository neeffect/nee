package pl.setblack.nee.ctx.web

import io.ktor.application.ApplicationCall
import io.ktor.request.header
import io.vavr.collection.List
import io.vavr.kotlin.option
import pl.setblack.nee.anyError
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
import pl.setblack.nee.effects.tx.TxEffect
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.security.DBUserRealm
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRealm
import pl.setblack.nee.security.UserRole
import java.sql.Connection
import java.util.concurrent.Executors

class EffectsInstance<R,G : TxProvider<R,G>> {
    val async = AsyncEffect<WebContext<R,G>>()
    fun secured(roles: List<UserRole>)
            = SecuredRunEffect<User, UserRole, WebContext<R,G>>(roles)
    val jdbc = TxEffect<Connection, WebContext<R,G>>().anyError()
    val cache = CacheEffect<WebContext<R,G>, Nothing>(CaffeineProvider()).anyError()
}

interface WebContextProvider<R, G : TxProvider<R, G>> {
    fun create(call: ApplicationCall): WebContext<R,G>

     fun effects() : EffectsInstance<R,G>
}

abstract class BaseWebContext<R, G : TxProvider<R, G>> : WebContextProvider<R,G> {

    private val effectsInstance = EffectsInstance<R,G>()

    override fun effects(): EffectsInstance<R, G>  = effectsInstance

    abstract val txProvider: TxProvider<R,G>

    abstract fun authProvider(call: ApplicationCall): SecurityProvider<User, UserRole>

    open val errorHandler: ErrorHandler by lazy { DefaultErrorHandler }

    abstract val executionContextProvider : ExecutionContextProvider

    override fun create(call: ApplicationCall)= WebContext(
        txProvider,
        authProvider(call),
        executionContextProvider,
        errorHandler,
        call
    )
}

abstract class JDBCBasedWebContext : BaseWebContext<Connection, JDBCProvider>() {

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
