package pl.setblack.nee.effects.jdbc

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

class JDBCConnection(private val connection: Connection) : TxConnection<Connection>, Logging {



    override fun begin(): Either<TxError, TxStarted<Connection>> =
        if (hasTransaction()) {
            val savepoint = getResource().setSavepoint()
            JDBCTransaction(this, some(savepoint))
        } else {
            getResource().autoCommit = false
            JDBCTransaction(this)
        }.let { Either.right<TxError, TxStarted<Connection>>(it) }

    //TODO handle in nested trx when
    override fun cont(): Either<TxError, TxStarted<Connection>> =
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
            conn.close()
        }
    }
}

class JDBCTransaction(val conn: JDBCConnection, val savepoint: Option<Savepoint> = none()) :
    TxConnection<Connection> by conn,
    TxStarted<Connection> {
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
}


class JDBCProvider(private  val connection: Connection) : TxProvider<Connection, JDBCProvider> {

    constructor(cfg: JDBCConfig) : this ( Class.forName(cfg.driverClassName).let {
        DriverManager.getConnection(cfg.url, cfg.user, cfg.password)
    })

    override fun getConnection(): TxConnection<Connection>  =
        JDBCConnection(connection)

    override fun setConnectionState(newState: TxConnection<Connection>): JDBCProvider =
        JDBCProvider(newState.getResource())

}

data class JDBCConfig(
    val driverClassName: String,
    val url: String,
    val user: String,
    val password: String = ""
)