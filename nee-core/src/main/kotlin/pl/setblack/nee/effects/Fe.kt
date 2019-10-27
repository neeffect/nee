package pl.setblack.nee.effects

import io.vavr.concurrent.Future
import io.vavr.control.Either
import pl.setblack.nee.merge

sealed class Fe<E, A> {

    abstract fun <B> map(f: (A) -> B): Fe<E, B>

    abstract fun <E1> mapLeft(f: (E) -> E1): Fe<E1, A>

    abstract fun <B> flatMap(f: (A) -> Fe<E, B>): Fe<E, B>

    abstract fun onComplete( f: (Either<E,A>)->Unit)

    abstract fun toFuture() : Future<Either<E,A>>

    companion object {
        internal fun <E,A> left(e:E) = InstantFe(Either.left<E,A>(e));
        internal fun <E,A> right(a:A) = InstantFe(Either.right<E,A>(a));


    }

    internal class InstantFe<E, A>(internal val v: Either<E, A>) : Fe<E, A>() {
        override fun toFuture(): Future<Either<E, A>>  = Future.successful(v)

        override fun onComplete(f: (Either<E, A>) -> Unit) = f(v)

        override fun <B> map(f: (A) -> B): Fe<E, B> = InstantFe(v.map(f))

        override fun <E1> mapLeft(f: (E) -> E1): Fe<E1, A> = InstantFe(v.mapLeft(f))

        override fun <B> flatMap(f: (A) -> Fe<E, B>): Fe<E, B> =
            v.map { a: A ->
                when (val res = f(a)) {
                    is InstantFe -> InstantFe(res.v)
                    is FutureFe ->  FutureFe(res.futureVal)
                }
            }.mapLeft { error:E -> this as Fe<E,B> } // a small cast for a compiler not a  change at all for the  runtime
                .merge()
    }

    internal class FutureFe<E, A>(internal val futureVal: Future<Either<E, A>>) : Fe<E, A>() {
        override fun toFuture(): Future<Either<E, A>>  = futureVal

        override fun onComplete(f: (Either<E, A>) -> Unit) = futureVal.onComplete {
            value ->
                f(value.get())
        }.let { Unit }

        override fun <B> map(f: (A) -> B): Fe<E, B> = FutureFe(futureVal.map { it.map(f) })

        override fun <E1> mapLeft(f: (E) -> E1): Fe<E1, A> = FutureFe(futureVal.map { it.mapLeft(f) })

        override fun <B> flatMap(f: (A) -> Fe<E, B>): Fe<E, B> =
            FutureFe(futureVal.flatMap { e: Either<E, A> ->
                e.map { a: A ->
                    val z: Future<Either<E, B>> = when (val res = f(a)) {
                        is FutureFe -> res.futureVal
                        is InstantFe -> Future.successful(res.v)
                    }
                    z
                }.mapLeft { e1 -> Future.successful(Either.left<E, B>(e1)) }
                .merge()
            })
    }
}


