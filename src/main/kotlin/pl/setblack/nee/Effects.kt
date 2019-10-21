package pl.setblack.nee

import io.vavr.control.Either

interface Effect<R, E> {
    fun <A, P> wrap(f: (R) -> (P) -> Either<E, A>): ((R) -> Pair<(P) -> Either<E, A>, R>)

    fun andThen(otherEffect: Effect<R, E>): Effect<R, E> =
        Effects.combine(otherEffect, this)

    fun <E1 : E> handleError(handler: (E) -> E1): Effect<R, E1> =
        HandleErrorEffect(this, handler)
}

class Effects<R1, R2, E1, E2>(
    private val inner: Effect<R1, E1>,
    private val outer: Effect<R2, E2>
) : Effect<R2, E1>
        where R2 : R1, E2 : E1 {
    override fun <A, P> wrap(f: (R2) -> (P) -> Either<E1, A>): (R2) -> Pair<(P) -> Either<E1, A>, R2> {
        val internalFunct = { r: R2 -> f(r) } as (R1) -> (P) -> Either<E1, A>
        val innerWrapped = inner.wrap(internalFunct)
                as (R2) -> Pair<(P) -> Either<E2, A>, R2>
        val outerF = { r: R2 ->
            { rn: R2 ->
                val z = innerWrapped(rn)
                Either.right<E2, (P)->Either<E2,A>>(z.first)
            }
        }

        val outerWrapped = outer.wrap(outerF)

        val func = { r: R2 ->
            val res = outerWrapped(r)
            val finalR = res.second
            //todo - ? finalR or r ?
            val called = res.first(finalR)
            called.map{Pair(it as (P)->Either<E1,A>, finalR) }
                .getOrElseGet { error->Pair({_:P->Either.left<E1,A>(error)},finalR) }
        }
        return func
//        val fp = { r: R2 -> f(r)} as (R2) -> (P) -> Either<E2, A>
//        val innerF = inner.wrap(fp) as (R2) -> Pair<(P) -> Either<E2, A>, R2>
//        val f2 = { r: R2 ->
//            val z = outer.wrap { r2 ->
//                val res = innerF(r)
//                res.first as (P)->Either<E1, A>
//            }
//            val result = z(res.second)
//            result as Pair<(P) -> Either<E1, A>, R2>
//        }
//        return f2
    }

    companion object {
        fun <R1, R2 : R1, E1, E2 : E1> combine(outer: Effect<R1, E1>, inner: Effect<R2, E2>): Effect<R2, E1> =
            Effects<R1, R2, E1, E2>(outer, inner)
    }
}

class NoEffect<R, E> : Effect<R, E> {
    override fun <A, P> wrap(f: (R) -> (P) -> Either<E, A>): (R) -> Pair<(P) -> Either<E, A>, R> =
        { r -> Pair(f(r), r) }
}

class HandleErrorEffect<R, E, E1 : E>(
    private val innerEffect: Effect<R, E>,
    private val handler: (E) -> E1
) : Effect<R, E1> {
    override fun <A, P> wrap(f: (R) -> (P) -> Either<E1, A>): (R) -> Pair<(P) -> Either<E1, A>, R> {
        val adoptedF = { r1: R ->
            { p2: P ->
                val innerRes = f(r1)(p2)
                innerRes
                    .mapLeft { it as E } //OMG what I am doing here
            }
        }
        val wrapped = innerEffect.wrap(adoptedF)
        return { r: R ->
            val result = wrapped(r)
            Pair({ p: P -> result.first(p).mapLeft(handler) }, result.second)
        }
    }
}

