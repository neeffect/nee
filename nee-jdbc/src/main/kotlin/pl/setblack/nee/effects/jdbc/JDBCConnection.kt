package pl.setblack.nee.effects.jdbc

import io.vavr.control.Either
import pl.setblack.nee.Logging
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxError
import pl.setblack.nee.effects.tx.TxStarted
import pl.setblack.nee.logger
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicReference

class JDBCConnection(private val cfg: JDBCConfig) : TxConnection<Connection>, Logging {
    private val connectionRef = AtomicReference<Connection>()

    init {
        Class.forName(cfg.driverClassName)
        connectionRef.set(DriverManager.getConnection(cfg.url, cfg.user, cfg.password))
    }

    override fun begin(): Either<TxError, TxStarted<Connection>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun cont(): Either<TxError, TxStarted<Connection>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasTransaction(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getResource(): Connection =
        this.connectionRef.get().let { connection ->
            assert(connection != null)
            connection
        }

    override fun close() = getResource().close()

}

data class JDBCConfig(
    val driverClassName: String,
    val url: String,
    val user: String,
    val password: String = ""
)