package pl.setblack.nee.effects

import io.vavr.concurrent.Future
import io.vavr.control.Either
import pl.setblack.nee.merge


//was Fe
sealed class Out<E, A> {

    abstract fun <B> map(f: (A) -> B): Out<E, B>

    abstract fun <E1> mapLeft(f: (E) -> E1): Out<E1, A>

    abstract fun <B> flatMap(f: (A) -> Out<E, B>): Out<E, B>

    abstract fun onComplete( f: (Either<E,A>)->Unit)

    abstract fun toFuture() : Future<Either<E,A>>

    companion object {
        internal fun <E,A> left(e:E) = InstantOut(Either.left<E,A>(e));
        internal fun <E,A> right(a:A) = InstantOut(Either.right<E,A>(a));
    }

    internal class InstantOut<E, A>(internal val v: Either<E, A>) : Out<E, A>() {
        override fun toFuture(): Future<Either<E, A>>  = Future.successful(v)

        override fun onComplete(f: (Either<E, A>) -> Unit) = f(v)

        override fun <B> map(f: (A) -> B): Out<E, B> = InstantOut(v.map(f))

        override fun <E1> mapLeft(f: (E) -> E1): Out<E1, A> = InstantOut(v.mapLeft(f))

        override fun <B> flatMap(f: (A) -> Out<E, B>): Out<E, B> =
            v.map { a: A ->
                when (val res = f(a)) {
                    is InstantOut -> InstantOut(res.v)
                    is FutureOut ->  FutureOut(res.futureVal)
                }
            }.mapLeft { error:E -> this as Out<E,B> } // a small cast for a compiler not a  change at all for the  runtime
                .merge()
    }

    internal class FutureOut<E, A>(internal val futureVal: Future<Either<E, A>>) : Out<E, A>() {
        override fun toFuture(): Future<Either<E, A>>  = futureVal

        override fun onComplete(f: (Either<E, A>) -> Unit) = futureVal.onComplete {
            value ->
                f(value.get())
        }.let { Unit }

        override fun <B> map(f: (A) -> B): Out<E, B> = FutureOut(futureVal.map { it.map(f) })

        override fun <E1> mapLeft(f: (E) -> E1): Out<E1, A> = FutureOut(futureVal.map { it.mapLeft(f) })

        override fun <B> flatMap(f: (A) -> Out<E, B>): Out<E, B> =
            FutureOut(futureVal.flatMap { e: Either<E, A> ->
                e.map { a: A ->
                    val z: Future<Either<E, B>> = when (val res = f(a)) {
                        is FutureOut -> res.futureVal
                        is InstantOut -> Future.successful(res.v)
                    }
                    z
                }.mapLeft { e1 -> Future.successful(Either.left<E, B>(e1)) }
                .merge()
            })
    }
}


