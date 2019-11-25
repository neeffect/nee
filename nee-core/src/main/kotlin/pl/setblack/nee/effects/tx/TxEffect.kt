package pl.setblack.nee.effects.tx

import io.vavr.collection.List
import io.vavr.control.Option
import pl.setblack.nee.Effect
import pl.setblack.nee.effects.Out
import java.lang.Exception


interface TxProvider<R, G : TxProvider<R, G>> {
    fun getConnection(): TxConnection<R>
    fun setConnectionState(newState: TxConnection<R>): G
}

interface TxError {
    fun txError(): TxErrorType
}

sealed class TxErrorType : TxError {
    override fun txError(): TxErrorType = this

    object NoConnection : TxErrorType()
    object CannotStartTransaction : TxErrorType()
    object CannotContinueTransaction : TxErrorType()
    object CannotCommitTransaction : TxErrorType()
    object CannotRollbackTransaction : TxErrorType()
    data class CannotQuery(val msg: String) : TxErrorType()
    data class MultipleErrors(val errors: List<TxError>) : TxErrorType()
    data class InternalException(val cause: java.lang.Exception) : TxErrorType()
}

class TxEffect<DB, R : TxProvider<DB, R>>(private val requiresNew: Boolean = false) : Effect<R, TxError> {
    override fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Out<TxError, A>, R> {
        return { res: R ->
            res.getConnection().let { connection ->
                try {
                    val continueOldTransaction = connection.hasTransaction() && !requiresNew
                    val tx = if (continueOldTransaction) {
                        connection.continueTx()
                    } else {
                        connection.begin()
                    }
                    val z = tx.map { startedTransaction ->
                        try {
                            val result = f(res)
                            val txFinished = if (continueOldTransaction) {
                                Pair(Option.none<TxError>(), connection)
                            } else {
                                startedTransaction.commit().also {
                                    it.second.close() //just added TODO - make it part of commit maybe?
                                }
                            }
                            val error = txFinished.first.map {
                                Pair({ _: P -> Out.left<TxError, A>(it) }, txFinished.second)
                            }.getOrElse {
                                Pair({ p: P -> Out.right<TxError, A>(result(p)) }, txFinished.second)
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
                                    { _: P ->
                                        Out.left<TxError, A>(
                                            TxErrorType.MultipleErrors(
                                                List.of(
                                                    TxErrorType.InternalException(e),
                                                    rollbackError
                                                )
                                            )
                                        )
                                    }, txCancelled.second
                                )
                            }.getOrElse {
                                Pair({ _: P ->
                                    Out.left<TxError, A>(
                                        TxErrorType.InternalException(
                                            e
                                        )
                                    )
                                }, txCancelled.second)
                            }
                        }
                    }.map {
                        Pair(it.first, res.setConnectionState(it.second))
                    }.getOrElseGet { error ->
                        Pair({ _: P -> Out.left<TxError, A>(error) }, res)
                    }
                    z
                } finally {

                }
            }
        }
    }
}