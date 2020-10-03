package pl.setblack.nee.ctx.web.support

import io.ktor.application.ApplicationCall
import pl.setblack.nee.ctx.web.BaseWebContext
import pl.setblack.nee.ctx.web.KtorThreadingModelTest
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.async.ECProvider
import pl.setblack.nee.effects.async.ExecutorExecutionContext
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.effects.security.SecurityCtx
import pl.setblack.nee.effects.security.SecurityError
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.effects.utils.invalid
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRole
import java.sql.Connection
import java.util.concurrent.Executors

internal open class EmptyTestContextProvider {
    val myTxProvider = object : TxProvider<Connection, JDBCProvider> {
        override fun getConnection(): TxConnection<Connection> =
            invalid()

        override fun setConnectionState(newState: TxConnection<Connection>): JDBCProvider =
            invalid()
    }

    val noSecurity = object : SecurityProvider<User, UserRole> {
        override fun getSecurityContext(): Out<SecurityError, SecurityCtx<User, UserRole>> =
            invalid()
    }

    val serverExecutor = Executors.newFixedThreadPool(KtorThreadingModelTest.reqs)
    val ec = ExecutorExecutionContext(serverExecutor)

    val contexProvider  = object : BaseWebContext<Connection, JDBCProvider>() {
        override val txProvider = myTxProvider
        override fun authProvider(call: ApplicationCall): SecurityProvider<User, UserRole> =
            noSecurity

        override val executionContextProvider = ECProvider(ec)
    }
}


internal object EmptyTestContext : EmptyTestContextProvider() {

}
