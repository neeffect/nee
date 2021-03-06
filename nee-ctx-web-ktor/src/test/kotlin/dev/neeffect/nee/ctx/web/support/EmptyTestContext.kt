package dev.neeffect.nee.ctx.web.support

import dev.neeffect.nee.ctx.web.BaseWebContextProvider
import dev.neeffect.nee.ctx.web.KtorThreadingModelTest
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.async.ECProvider
import dev.neeffect.nee.effects.async.ExecutorExecutionContext
import dev.neeffect.nee.effects.jdbc.JDBCProvider
import dev.neeffect.nee.effects.security.SecurityCtx
import dev.neeffect.nee.effects.security.SecurityError
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.effects.tx.TxConnection
import dev.neeffect.nee.effects.tx.TxProvider
import dev.neeffect.nee.effects.utils.invalid
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import io.ktor.application.ApplicationCall
import java.sql.Connection
import java.util.concurrent.Executors

internal open class EmptyTestContextProvider {
    open val myTxProvider = object : TxProvider<Connection, JDBCProvider> {
        override fun getConnection(): TxConnection<Connection> =
            invalid()

        override fun setConnectionState(newState: TxConnection<Connection>): JDBCProvider =
            invalid()
    }

    open fun security(call: ApplicationCall) = object : SecurityProvider<User, UserRole> {
        override fun getSecurityContext(): Out<SecurityError, SecurityCtx<User, UserRole>> =
            invalid()
    }

    val serverExecutor = Executors.newFixedThreadPool(KtorThreadingModelTest.reqs)
    val ec = ExecutorExecutionContext(serverExecutor)

    val contexProvider = object : BaseWebContextProvider<Connection, JDBCProvider>() {
        override val txProvider = myTxProvider
        override fun authProvider(call: ApplicationCall): SecurityProvider<User, UserRole> =
            security(call)

        override val executionContextProvider = ECProvider(ec)
    }
}

internal object EmptyTestContext : EmptyTestContextProvider()
