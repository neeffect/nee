package dev.neeffect.nee.effects

import dev.neeffect.nee.effects.utils.merge
import io.vavr.concurrent.Future
import io.vavr.control.Either
import kotlinx.coroutines.future.await

/**
 * Outcome of business function.
 *
 * It is ~ Future<Either<E,A>> (in vavr style0
 * the reason for not using vavr here was:
 *  - making critical api less depending on vavr
 *  - some efficiency (when result is in fdact immediate (see InstantOut)
 *
 *
 */
sealed class Out<out E, out A> {

    abstract fun <B> map(f: (A) -> B): Out<E, B>

    abstract fun <E1> mapLeft(f: (E) -> E1): Out<E1, A>

    fun <E1, B> handle(fe: (E) -> Out<E1, B>, fa: (A) -> B): Out<E1, B> =

        this.mapLeft(fe).map { a -> Out.right<E1, B>(fa(a)) }.let { result: Out<Out<E1, B>, Out<E1, B>> ->
            when (result) {
                is FutureOut -> FutureOut(result.futureVal.map {
                    Either.right<E1, Out<E1, B>>(it.merge())
                }).flatMap { it }
                is InstantOut -> result.v.merge()
            }
        }

    companion object {
        fun <E, A> left(e: E): Out<E, A> = InstantOut(Either.left<E, A>(e))
        fun <E, A> right(a: A): Out<E, A> = InstantOut(Either.right<E, A>(a))

        fun <E, A> fromFuture(future: Future<Either<E, A>>): Out<E, A> = FutureOut(future)
        fun <E, A> right(future: Future<A>): Out<E, A> = fromFuture(future.map { Either.right<E, A>(it) })
    }

    internal class InstantOut<E, A>(internal val v: Either<E, A>) : Out<E, A>() {
        fun toFutureInternal(): Future<Either<E, A>> = Future.successful(v)

        // override fun onComplete(f: (Either<E, out A>) -> Unit) = f(v)

        override fun <B> map(f: (A) -> B): Out<E, B> = InstantOut(v.map(f))

        override fun <E1> mapLeft(f: (E) -> E1): Out<E1, A> = InstantOut(v.mapLeft(f))

        fun <B> flatMapInternal(f: (A) -> Out<E, B>): Out<E, B> =
            v.map { a: A ->
                when (val res = f(a)) {
                    is InstantOut -> InstantOut(res.v)
                    is FutureOut -> FutureOut(res.futureVal)
                }
            }.mapLeft { _: E ->
                @Suppress("UNCHECKED_CAST")
                this as Out<E, B>
            }.merge()

        fun k(): suspend () -> Either<E, out A> = { v }
    }

    internal class FutureOut<E, A>(internal val futureVal: Future<Either<E, A>>) : Out<E, A>() {
        fun toFutureInternal(): Future<Either<E, A>> = futureVal

        override fun <B> map(f: (A) -> B): Out<E, B> = FutureOut(futureVal.map { it.map(f) })

        override fun <E1> mapLeft(f: (E) -> E1): Out<E1, A> = FutureOut(futureVal.map { it.mapLeft(f) })

        fun <B> flatMapInternal(f: (A) -> Out<E, B>): Out<E, B> =
            FutureOut(futureVal.flatMap { e: Either<E, A> ->
                e.map { a: A ->
                    val z: Future<Either<E, B>> = when (val res = f(a)) {
                        is FutureOut -> res.futureVal
                        is InstantOut -> Future.successful(futureVal.executor(), res.v)
                    }
                    z
                }.mapLeft { e1 -> Future.successful(futureVal.executor(), Either.left<E, B>(e1)) }
                    .merge()
            })

        fun k(): suspend () -> Either<E, out A> = {
            futureVal.toCompletableFuture().await()
        }
    }
}

fun <E, A, B> Out<E, A>.flatMap(f: (A) -> Out<E, B>): Out<E, B> =
    when (this) {
        is Out.InstantOut -> this.flatMapInternal(f)
        is Out.FutureOut -> this.flatMapInternal(f)
    }

fun <E, A> Out<E, A>.toFuture(): Future<Either<E, A>> = when (this) {
    is Out.InstantOut -> this.toFutureInternal()
    is Out.FutureOut -> this.toFutureInternal()
}
