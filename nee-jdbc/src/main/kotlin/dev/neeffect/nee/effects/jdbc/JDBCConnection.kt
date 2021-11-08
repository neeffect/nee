package dev.neeffect.nee.effects.jdbc

import com.mchange.v2.c3p0.ComboPooledDataSource
import dev.neeffect.nee.effects.tx.TxConnection
import dev.neeffect.nee.effects.tx.TxError
import dev.neeffect.nee.effects.tx.TxProvider
import dev.neeffect.nee.effects.tx.TxStarted
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.effects.utils.logger
import io.vavr.control.Either
import io.vavr.control.Option
import io.vavr.control.Option.none
import io.vavr.kotlin.some
import java.io.PrintWriter
import java.sql.Connection
import java.sql.Savepoint
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * Standard jdbc connection.
 */
class JDBCConnection(
    private val connection: Connection,
    private val close: Boolean = false
) : TxConnection<Connection>,
    Logging {
    override fun begin(): Either<TxError, TxStarted<Connection>> =
        if (hasTransaction()) {
            val savepoint = getResource().setSavepoint()
            JDBCTransaction(this, some(savepoint))
        } else {
            getResource().autoCommit = false
            JDBCTransaction(this)
        }.let { Either.right<TxError, TxStarted<Connection>>(it) }

    // TODO handle in nested trx when
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
            if (!savepoint.isDefined ) {
                getResource().autoCommit = true
            }
            Pair(Option.none(), conn) // TODO what about autocommit? NOT Tested
        }

    override fun rollback(): Pair<Option<TxError>, TxConnection<Connection>> =
        this.savepoint.map { sp ->
            getResource().rollback(sp)
            Pair(Option.none<TxError>(), conn)
        }.getOrElse {
            getResource().rollback()
            Pair(Option.none<TxError>(), conn)
        }

    @Suppress("ReturnUnit")
    override fun close() {
        logger().info("we do not close ongoing transaction")
    }
}

/**
 * Provider of jdbc connection.
 */
class JDBCProvider(
    private val connection: ConnectionWrapper,
    private val close: Boolean = false
) : TxProvider<Connection, JDBCProvider> {
    constructor(connection: Connection) : this(ConnectionWrapper.DirectConnection(connection))

    constructor(cfg: JDBCConfig) : this(Class.forName(cfg.driverClassName).let {
        val pool = ComboPooledDataSource()
        pool.user = cfg.user
        pool.password = cfg.password
        pool.jdbcUrl = cfg.url
        ConnectionWrapper.PooledConnection(pool)
    }, true)

    fun dataSource(): DataSource = connection.dataSource()

    override fun getConnection(): TxConnection<Connection> =
        JDBCConnection(connection.conn(), close)

    override fun setConnectionState(newState: TxConnection<Connection>): JDBCProvider =
        JDBCProvider(ConnectionWrapper.DirectConnection(newState.getResource()))
}

sealed class ConnectionWrapper {

    abstract fun conn(): Connection

    open fun dataSource(): DataSource = WrappedDataSource(conn())

    data class DirectConnection(private val connection: Connection) : ConnectionWrapper() {
        override fun conn(): Connection = connection
    }

    data class PooledConnection(private val pool: ComboPooledDataSource) : ConnectionWrapper() {
        override fun conn(): java.sql.Connection = pool.connection

        override fun dataSource(): DataSource = pool
    }
}

data class JDBCConfig(
    val driverClassName: String,
    val url: String,
    val user: String,
    val password: String = ""
)


class WrappedSqlConnection(connection: Connection) : Connection by connection {
    override fun close() {
        println("ignored closing  connection")
    }
}

// TODO provide tests (useful in testing tools)
class WrappedDataSource(private val conn: Connection) : DataSource {
    override fun getLogWriter(): PrintWriter {
        TODO("Not yet implemented")
    }

    override fun setLogWriter(out: PrintWriter?) {
        TODO("Not yet implemented")
    }

    override fun setLoginTimeout(seconds: Int) {
        TODO("Not yet implemented")
    }

    override fun getLoginTimeout(): Int {
        TODO("Not yet implemented")
    }

    override fun getParentLogger(): Logger {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        TODO("Not yet implemented")
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getConnection(): Connection = WrappedSqlConnection(this.conn)

    override fun getConnection(username: String?, password: String?): Connection = getConnection()
}
