package pl.setblack.nee.effects.tx

import io.vavr.collection.List
import io.vavr.control.Either
import io.vavr.control.Option
import pl.setblack.nee.Effect
import java.lang.Exception

interface TxConnection<R> {
    fun begin(): Either<TxError, TxStarted<R>>

    fun cont(): Either<TxError, TxStarted<R>>

    fun hasTransaction(): Boolean

    fun getResource(): R
}

interface TxStarted<R> : TxConnection<R> {

    fun commit(): Pair<Option<TxError>, TxConnection<R>>

    fun rollback(): Pair<Option<TxError>, TxConnection<R>>

}

interface TxProvider<R, G : TxProvider<R, G>> {
    fun getConnection(): TxConnection<R>
    fun setConnectionState(newState: TxConnection<R>): G
}

interface TxError {
    fun txError() : TxErrorType
}

sealed class TxErrorType : TxError {
    override fun txError(): TxErrorType  = this

    object NoConnection : TxErrorType()
    object CannotStartTransaction : TxErrorType()
    object CannotContinueTransaction : TxErrorType()
    object CannotCommitTransaction : TxErrorType()
    object CannotRollbackTransaction : TxErrorType()
    data class CannotQuery(val msg: String) : TxErrorType()

    data class MultipleErrors(val errors: List<TxError>) : TxErrorType()
    data class InternalException(val cause: java.lang.Exception) : TxErrorType()
}

class TxEffect<R, P : TxProvider<R, P>>(private  val requiresNew : Boolean = false) : Effect<P, TxError> {
    override fun <A> wrap(f: (P) -> Either<TxError, A>): (P) -> Pair<Either<TxError, A>, P> {
        return { res: P ->
            val connection = res.getConnection()
            val continueOldTransaction = connection.hasTransaction() && !requiresNew
            val tx = if (continueOldTransaction) {
                connection.cont()
            } else {
                connection.begin()
            }
            tx.map { startedTransaction ->
                try {
                    val result = f(res)
                    val txFinished = if (continueOldTransaction) {
                        Pair(Option.none<TxError>(), connection)
                    } else {
                        startedTransaction.commit()
                    }
                    val error = txFinished.first.map {
                        Pair(Either.left<TxError, A>(it), txFinished.second)
                    }.getOrElse {
                        Pair(result, txFinished.second)
                    }
                    error
                } catch (e: Exception) {
                    val txCancelled = if (continueOldTransaction) {
                        Pair(Option.none<TxError>(), connection)
                    } else {
                        startedTransaction.rollback()
                    }
                    txCancelled.first.map { rollbackError ->
                        Pair(
                            Either.left<TxError, A>(
                                TxErrorType.MultipleErrors(
                                    List.of(
                                        TxErrorType.InternalException(e),
                                        rollbackError
                                    )
                                )
                            ), txCancelled.second
                        )
                    }.getOrElse {
                        Pair(Either.left<TxError, A>(
                            TxErrorType.InternalException(
                                e
                            )
                        ), txCancelled.second)
                    }
                }
            }.map {
                Pair(it.first, res.setConnectionState(it.second))
            }.getOrElseGet { error ->
                Pair(Either.left<TxError, A>(error), res)
            }
        }
    }
}