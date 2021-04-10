package dev.neeffect.nee.effects.tx

import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.async.executeAsyncCleaning
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.effects.utils.logger
import io.vavr.collection.List
import io.vavr.control.Option
import java.util.concurrent.atomic.AtomicLong

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
class TxEffect<DB, R : TxProvider<DB, R>>(private val requiresNew: Boolean = false) : Effect<R, TxError>, Logging {

    @Suppress("TooGenericExceptionCaught")
    override fun <A> wrap(f: (R) -> A): (R) -> Pair<Out<TxError, A>, R> = { res: R ->
        val txNumber = txCounter.getAndIncrement()
        res.getConnection().let { connection ->
            val continueOldTransaction = connection.hasTransaction() && !requiresNew
            val tx = if (continueOldTransaction) {
                logger().debug("continuing Tx ($txNumber)")
                connection.continueTx()
            } else {
                logger().debug("beginning Tx ($txNumber)")
                connection.begin()
            }
            tx.map { startedTransaction ->
                try {
                    val newRes = res.setConnectionState(startedTransaction)
                    doInTransaction(f, newRes, continueOldTransaction, startedTransaction, txNumber)
                } catch (e: Exception) {
                    handleTxException<A>(continueOldTransaction, connection, startedTransaction, e)
                }
            }.map {
                Pair(it.first, res.setConnectionState(it.second))
            }.getOrElseGet { error ->
                Pair(Out.left<TxError, A>(error), res)
            }
        }
    }

    private fun <A> handleTxException(
        continueOldTransaction: Boolean,
        connection: TxConnection<DB>,
        startedTransaction: TxStarted<DB>,
        e: Exception
    ): Pair<Out<TxError, A>, TxConnection<DB>> =
        if (continueOldTransaction) {
            Pair(Option.none<TxError>(), connection)
        } else {
            logger().debug("tx rollback")
            startedTransaction.rollback()
        }.let { txCancelled ->
            txCancelled.first.map { rollbackError ->
                Pair(

                    Out.left<TxError, A>(
                        TxErrorType.MultipleErrors(
                            List.of(
                                TxErrorType.InternalException(e),
                                rollbackError
                            )
                        )
                    ), txCancelled.second
                )
            }.getOrElse {
                Pair(
                    Out.left<TxError, A>(
                        TxErrorType.InternalException(
                            e
                        )
                    ), txCancelled.second
                )
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private fun <A> doInTransaction(
        f: (R) -> A,
        res: R,
        continueOldTransaction: Boolean,
        startedTransaction: TxStarted<DB>,
        txNumber: Long
    ): Pair<Out<TxError, A>, TxStarted<DB>> =
        executeAsyncCleaning(res, {
            f(res)
        }, { r ->
            if (!continueOldTransaction) {
                logger().debug("commiting Tx ($txNumber)")
                startedTransaction.commit().also {
                    it.second.close()
                }
                r
            } else {
                logger().debug("not commited Tx ($txNumber) - continued")
                r
            }
        }).let { result ->
            Pair(
                try {
                    Out.right<TxError, A>(result)
                } catch (e: Exception) {
                    Out.left<TxError, A>(TxErrorType.InternalException(e))
                }, startedTransaction
            )
        }

    companion object {
        private val txCounter = AtomicLong()
    }
}
