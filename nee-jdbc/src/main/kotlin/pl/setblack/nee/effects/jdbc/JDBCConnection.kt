package pl.setblack.nee.effects.jdbc

import io.vavr.control.Either
import io.vavr.control.Option
import pl.setblack.nee.Logging
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxError
import pl.setblack.nee.effects.tx.TxStarted
import pl.setblack.nee.logger
import java.sql.Connection
import java.sql.DriverManager
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

    override fun cont(): Either<TxError, TxStarted<Connection>> =
        Either.right<TxError, TxStarted<Connection>>(JDBCTransaction(this)).also {
            if (!hasTransaction()) {
                getResource().autoCommit = false
            }
        }

    override fun hasTransaction(): Boolean = !this.getResource().autoCommit


    override fun getResource(): Connection =
        this.connectionRef.get().let { connection ->
            assert(connection != null)
            connection
        }

    override fun close(): Unit = getResource().let { conn ->
        if (conn.isClosed) {
            logger().warn("connection already closed")
        } else {
            conn.close()
        }
    }
}

class JDBCTransaction(val conn: JDBCConnection) : TxConnection<Connection> by conn,
    TxStarted<Connection> {
    override fun commit(): Pair<Option<TxError>, TxConnection<Connection>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun rollback(): Pair<Option<TxError>, TxConnection<Connection>> =
        getResource().rollback().let {
            Pair(Option.none(), conn)
        }

}

data class JDBCConfig(
    val driverClassName: String,
    val url: String,
    val user: String,
    val password: String = ""
)