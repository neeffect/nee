package pl.setblack.nee.effects.jdbc

import com.mchange.v2.c3p0.ComboPooledDataSource
import io.vavr.control.Either
import io.vavr.control.Option
import io.vavr.control.Option.none
import io.vavr.kotlin.some
import pl.setblack.nee.Logging
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxError
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.effects.tx.TxStarted
import pl.setblack.nee.logger
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Savepoint
import java.util.concurrent.atomic.AtomicReference

class JDBCConnection(private val connection: Connection, val close: Boolean = false ) : TxConnection<Connection>, Logging {
    override fun begin(): Either<TxError, TxStarted<Connection>> =
        if (hasTransaction()) {
            val savepoint = getResource().setSavepoint()
            JDBCTransaction(this, some(savepoint))
        } else {
            getResource().autoCommit = false
            JDBCTransaction(this)
        }.let { Either.right<TxError, TxStarted<Connection>>(it) }

    //TODO handle in nested trx when
    override fun continueTx(): Either<TxError, TxStarted<Connection>> =
        Either.right<TxError, TxStarted<Connection>>(JDBCTransaction(this)).also {
            if (!hasTransaction()) {
                getResource().autoCommit = false
            }
        }

    override fun hasTransaction(): Boolean = !this.getResource().autoCommit

    override fun getResource(): Connection = this.connection

    override fun close(): Unit = getResource().let { conn ->
        if (conn.isClosed) {
            logger().warn("connection already closed")
        } else {
            if (close) {
                conn.close()
            }
        }
    }
}

class JDBCTransaction(val conn: JDBCConnection, val savepoint: Option<Savepoint> = none()) :
    TxConnection<Connection> by conn,
    TxStarted<Connection>,
    Logging {
    override fun commit(): Pair<Option<TxError>, TxConnection<Connection>> =
        getResource().commit().let {
            Pair(Option.none(), conn) //TODO what about autocommit?
        }

    override fun rollback(): Pair<Option<TxError>, TxConnection<Connection>> =
        this.savepoint.map { sp ->
            getResource().rollback(sp)
            Pair(Option.none<TxError>(), conn)
        }.getOrElse {
            getResource().rollback()
            Pair(Option.none<TxError>(), conn)
        }

    override fun close() {
        logger().info("we do not close ongoing transaction")
    }
}


class JDBCProvider(private  val connection: ConnectionWrapper, private val close: Boolean = false) : TxProvider<Connection, JDBCProvider> {
    constructor(connection: Connection) : this(ConnectionWrapper.DirectConnection(connection))

    constructor(cfg: JDBCConfig) : this ( Class.forName(cfg.driverClassName).let {
        val pool = ComboPooledDataSource()
        pool.user = cfg.user
        pool.password =cfg.password
        pool.jdbcUrl = cfg.url
        ConnectionWrapper.PooledConnection(pool)
    }, true)
    override fun getConnection(): TxConnection<Connection>  =
        JDBCConnection(connection.conn(), close)
    override fun setConnectionState(newState: TxConnection<Connection>): JDBCProvider =
        JDBCProvider(ConnectionWrapper.DirectConnection(newState.getResource()))
}

sealed class ConnectionWrapper {

    abstract  fun conn() : Connection

    data class DirectConnection(private val connection: Connection) : ConnectionWrapper() {
        override fun conn(): Connection  = connection
    }

    data class PooledConnection(private val pool : ComboPooledDataSource) : ConnectionWrapper() {
        override fun conn(): java.sql.Connection  = pool.connection
    }
}

data class JDBCConfig(
    val driverClassName: String,
    val url: String,
    val user: String,
    val password: String = ""
)