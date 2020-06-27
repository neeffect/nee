package pl.setblack.nee

import io.vavr.control.Either
import pl.setblack.nee.effects.Out

/**
 * An effect, or maybe aspect :-)
 *
 * @param R some environment param used by effect and business function
 * @param E error caused by this effect
 */
interface Effect<R, E> {
    /**
     * Wrap a business function in a given effect
     *
     * Gives back "wrapped function"
     */
    fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Out<E, A>, R>

    /**
     * Installs error handler (kind of mapLeft).
     *
     * Returns new Effect.
     */
    fun <XE> handleError(handler: (E) -> XE): Effect<R, XE> =
        HandleErrorEffect(this, handler)
}

/**
 * Composition of effects.
 *
 * (Notice: it is actually like kleisli composition of monads...
 * maybe in fact Nee and Effect should be a one type?)
 */
fun <R1, E1, R2 : R1, E2> Effect<R2, E2>.andThen(otherEffect: Effect<R1, E1>) = Effects.combine(otherEffect, this)


class Effects<R1, R2, E1, E2>(
    private val inner: Effect<R1, E1>,
    private val outer: Effect<R2, E2>
) : Effect<R2, Either<E1, E2>>
        where R2 : R1 {

    //    override fun <A, P> wrap(f: (R2) -> (P) -> A): (R2) -> Pair<(P) -> Out<Either<E1, E2>, A>, R2> {
//        @Suppress("UNCHECKED_CAST")
//        val internalFunct = { r: R2 -> f(r) } as (R1) -> (P) -> A
//        val innerWrapped =
//            inner.handleError { error: E1 -> Either.left<E1, E2>(error) }
//                .wrap(internalFunct)
//        val outerF = { _: R2 ->
//            { rn: R2 ->
//                val z = innerWrapped(rn)
//                z.first
//            }
//        }
//
//        val outerWrapped = outer
//            .handleError { error -> Either.right<E1, E2>(error) }
//            .wrap(outerF)
//        return    { r: R2 ->
//                val res = outerWrapped(r)
//                val finalR = res.second
//                //TODO - ? finalR or r ?
//                val called = res.first(finalR)
//                val x= { p:P ->
//                    called.flatMap { fp -> fp(p) }
//                }
//                Pair(x, finalR)
//            }
//    }
    override fun <A, P> wrap(f: (R2) -> (P) -> A): (R2) -> Pair<(P) -> Out<Either<E1, E2>, A>, R2> {
        @Suppress("UNCHECKED_CAST")
        val internalFunct = { r: R2 -> f(r) } as (R1) -> (P) -> A
        val innerWrapped =
            inner.handleError { error: E1 -> Either.left<E1, E2>(error) }
                .wrap(internalFunct)
        val outerF =
            { rn: R2 ->
                val z = innerWrapped(rn)
                z.first
            }


        val outerWrapped = outer
            .handleError { error -> Either.right<E1, E2>(error) }
            .wrap(outerF)
        return { r: R2 ->
            val res = outerWrapped(r)
            val finalR = res.second
            //TODO - ? finalR or r ?
            val called = res.first
            val x = { p: P ->
                val z =called(p).flatMap { it }
                z
            }
            Pair(x, finalR)
        }
    }

    companion object {
        fun <R1, R2 : R1, E1, E2> combine(outer: Effect<R1, E1>, inner: Effect<R2, E2>): Effect<R2, Either<E1, E2>> =
            Effects<R1, R2, E1, E2>(outer, inner)
    }
}

class NoEffect<R, E> : Effect<R, E> {
    override fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Out<E, A>, R> =
        { r -> Pair({ p -> Out.right<E, A>(f(r)(p)) }, r) }
}

class HandleErrorEffect<R, E, E1>(
    private val innerEffect: Effect<R, E>,
    private val handler: (E) -> E1
) : Effect<R, E1> {
    override fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Out<E1, A>, R> {
        val wrapped = innerEffect.wrap(f)
        return { r: R ->
            val result = wrapped(r)
            Pair({ p: P -> result.first(p).mapLeft(handler) }, result.second)
        }
    }
}


fun <R, E> Effect<R, E>.anyError(): Effect<R, Any> = HandleErrorEffect(this) { it as Any }
