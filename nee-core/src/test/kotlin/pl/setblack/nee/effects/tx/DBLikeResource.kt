package pl.setblack.nee.effects.tx

import io.vavr.control.Either
import io.vavr.control.Option
import java.lang.IllegalStateException

class DBLikeProvider(
        val db: DBLike,
        val conn: TxConnection<DBLike> = DBConnection(db)
        ) :
    TxProvider<DBLike, DBLikeProvider> {
    override fun getConnection(): TxConnection<DBLike> =
        if (!db.connected()) {
            if (db.connect()) {
                conn
            } else {
                throw IllegalStateException("cannot connect DB")
            }
        } else {
            conn
        }

    override fun setConnectionState(newState: TxConnection<DBLike>) =
        DBLikeProvider(db, newState)
}


internal open class DBConnection(val db: DBLike) : TxConnection<DBLike> {
    override fun hasTransaction(): Boolean  = db.transactionLevel() > 0
    override fun begin(): Either<TxError, TxStarted<DBLike>> =
        if (db.begin()) {
            Either.right(DBTxConnection(db))
        } else {
            Either.left<TxError, TxStarted<DBLike>>(
                TxErrorType.CannotStartTransaction)
        }

    override fun cont(): Either<TxError, TxStarted<DBLike>> =
        if (db.continueTransaction()) {
            Either.right(DBTxConnection(db))
        } else {
            Either.left<TxError, TxStarted<DBLike>>(
                TxErrorType.CannotContinueTransaction)
        }

    override fun getResource(): DBLike = db

    override fun close() {
        db.close()
    }
}

internal class DBTxConnection(db: DBLike) : DBConnection(db), TxStarted<DBLike> {

    override fun commit(): Pair<Option<TxError>, TxConnection<DBLike>> =
        if (db.commit()) {
            Pair(Option.none<TxError>(), DBConnection(db))
        } else {
            Pair(Option.some<TxError>(TxErrorType.CannotCommitTransaction), this)
        }

    override fun rollback(): Pair<Option<TxError>, TxConnection<DBLike>> =
        if (db.rollback()) {
            Pair(Option.none<TxError>(), DBConnection(db))
        } else {
            Pair(Option.some<TxError>(TxErrorType.CannotRollbackTransaction), this)
        }
}