package dev.neeffect.nee.effects.tx

import io.vavr.control.Either
import io.vavr.control.Option
import java.io.Closeable

interface TxConnection<R> : Closeable {
    fun begin(): Either<TxError, TxStarted<R>>

    fun continueTx(): Either<TxError, TxStarted<R>>

    fun hasTransaction(): Boolean

    fun getResource(): R
}

interface TxStarted<R> : TxConnection<R> {
    fun commit(): Pair<Option<TxError>, TxConnection<R>>

    fun rollback(): Pair<Option<TxError>, TxConnection<R>>
}
