package dev.neeffect.nee.effects.tx

import io.vavr.control.Either
import io.vavr.control.Option

internal class DBLikeProvider(
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


internal open class DBConnection(val db: DBLike, val level: Int = 0) : TxConnection<DBLike> {
    override fun hasTransaction(): Boolean = false


    override fun begin(): Either<TxError, TxStarted<DBLike>> =
        if (db.begin()) {
            Either.right(DBTxConnection(db, level + 1))
        } else {
            Either.left<TxError, TxStarted<DBLike>>(
                TxErrorType.CannotStartTransaction
            )
        }

    override fun continueTx(): Either<TxError, TxStarted<DBLike>> =
        if (db.continueTransaction()) {
            Either.right(DBTxConnection(db, level))
        } else {
            Either.left<TxError, TxStarted<DBLike>>(
                TxErrorType.CannotContinueTransaction
            )
        }

    override fun getResource(): DBLike = db

    override fun close() {
        db.close()
    }
}

internal class DBTxConnection(db: DBLike, level: Int) : DBConnection(db, level), TxStarted<DBLike> {

    override fun hasTransaction(): Boolean = true


    override fun commit(): Pair<Option<TxError>, TxConnection<DBLike>> =
        if (db.commit()) {
            Pair(Option.none<TxError>(), DBConnection(db, level - 1))
        } else {
            Pair(Option.some<TxError>(TxErrorType.CannotCommitTransaction), this)
        }

    override fun rollback(): Pair<Option<TxError>, TxConnection<DBLike>> =
        if (db.rollback()) {
            Pair(Option.none<TxError>(), DBConnection(db, level - 1))
        } else {
            Pair(Option.some<TxError>(TxErrorType.CannotRollbackTransaction), this)
        }
}
