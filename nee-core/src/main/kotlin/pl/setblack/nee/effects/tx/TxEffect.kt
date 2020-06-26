package pl.setblack.nee.effects.tx

import io.vavr.collection.List
import io.vavr.control.Option
import pl.setblack.nee.Effect
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.async.AsyncStack
import pl.setblack.nee.effects.async.AsyncSupport
import pl.setblack.nee.effects.async.executeAsyncCleaning

/**
 *  Transactional resource provider.
 */
interface TxProvider<R, G : TxProvider<R, G>> {
    fun getConnection(): TxConnection<R>
    fun setConnectionState(newState: TxConnection<R>): G
}

/**
 * Error supplier in transaction effect.
 */
interface TxError {
    fun txError(): TxErrorType
}

/**
 * Errors.
 */
sealed class TxErrorType : TxError {
    override fun txError(): TxErrorType = this

    /**
     * No connection (provider) available.
     */
    object NoConnection : TxErrorType()

    /**
     * Tx cannot be started.
     */
    object CannotStartTransaction : TxErrorType()

    /**
     * Cannot run operation in parent transaction,
     */
    object CannotContinueTransaction : TxErrorType()

    /**
     * Transaction cannot be committed.
     */
    object CannotCommitTransaction : TxErrorType()

    /**
     * Transaction cannot be rolled back.
     */
    object CannotRollbackTransaction : TxErrorType()

    /**
     * Query on connection cannot be processed.
     */
    data class CannotQuery(val msg: String) : TxErrorType()

    /**
     * Mulitple errors occured.
     *
     * Errors list contains subsequent errors. May be recursive.
     */
    data class MultipleErrors(val errors: List<TxError>) : TxErrorType()

    /**
     * Unhandled java exception occured.
     */
    data class InternalException(val cause: java.lang.Exception) : TxErrorType()
}

/**
 * Transaction like effect.
 *
 * Use for SQL/JDBC operations, but anything that works on some "resource - conection"
 * can be implemented using TxProvider interface.
 *
 * @param DB a resource,  a connection
 * @param R  provider of resource (also must support update of connection state)
 */
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
                            val newRes = res.setConnectionState(startedTransaction)
                            doInTransaction(f, newRes, continueOldTransaction, startedTransaction)
                        } catch (e: Exception) {
                            handleTxException<A, P>(continueOldTransaction, connection, startedTransaction, e)
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

    private fun <A, P> handleTxException(
        continueOldTransaction: Boolean,
        connection: TxConnection<DB>,
        startedTransaction: TxStarted<DB>,
        e: Exception
    ): Pair<(P) -> Out<TxError, A>, TxConnection<DB>> =
        if (continueOldTransaction) {
            Pair(Option.none<TxError>(), connection)
        } else {
            startedTransaction.rollback()
        }.let { txCancelled ->
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


    private fun <A, P> doInTransaction(
        f: (R) -> (P) -> A,
        res: R,
        continueOldTransaction: Boolean,
        startedTransaction: TxStarted<DB>
    ): Pair<(P) -> Out<TxError, A>, TxStarted<DB>> {
        val result = { p: P ->
            executeAsyncCleaning(res,{
                f(res)(p)
            },{r->
                if (!continueOldTransaction) {
                    startedTransaction.commit().also {
                        it.second.close() //just added TODO - make it part of commit maybe?
                    }
                }
                r
            })

        }
        return Pair({ p: P ->
            try {
                Out.right<TxError, A>(result(p))
            } catch (e: Exception) {
                Out.left<TxError, A>(TxErrorType.InternalException(e))
            }
        }, startedTransaction)
    }
}
