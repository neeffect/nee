package pl.setblack.nee.ctx.web

import io.ktor.application.ApplicationCall
import io.ktor.request.header
import io.vavr.kotlin.option
import pl.setblack.nee.effects.async.ECProvider
import pl.setblack.nee.effects.async.ExecutorExecutionContext
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.security.DBUserRealm
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRealm
import pl.setblack.nee.security.UserRole
import java.util.concurrent.Executors

interface WebContextProvider {
    fun create(call: ApplicationCall): WebContext
}

abstract class JDBCBasedWebContext : WebContextProvider{
    private val jdbcTasksScheduler = Executors.newFixedThreadPool(4)

    private val jdbcExecutionContextProvider =
        ECProvider(ExecutorExecutionContext(jdbcTasksScheduler))

    abstract val jdbcConfig: JDBCConfig

    open val jdbcProvider: JDBCProvider by lazy {
        JDBCProvider(jdbcConfig)
    }

    open val userRealm: UserRealm<User, UserRole> by lazy {
        DBUserRealm(jdbcProvider)
    }

    open fun authProvider(call: ApplicationCall): SecurityProvider<User, UserRole> =
        BasicAuthProvider<User, UserRole>(
            call.request.header("Authorization").option(),
            userRealm
        )

    override fun create(call: ApplicationCall): WebContext = WebContext(
        jdbcProvider,
        authProvider(call),
        jdbcExecutionContextProvider,
        call
    )

}