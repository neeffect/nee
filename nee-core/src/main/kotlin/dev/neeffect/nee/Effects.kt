package dev.neeffect.nee

import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.flatMap
import dev.neeffect.nee.effects.utils.merge
import io.vavr.control.Either

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
    fun <A> wrap(f: (R) -> A): (R) -> Pair<Out<E, A>, R>

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

infix fun <R1, E1, R2 : R1, E2> Effect<R2, E2>.then(otherEffect: Effect<R1, E1>) = Effects.combine(otherEffect, this)

infix fun <R1, E1, R2 : R1, E2 : E1> Effect<R2, E2>.with(otherEffect: Effect<R1, E1>) =
    Effects.combine(otherEffect, this).handleError { error: Either<E1, E2> ->
        error.map { it as E1 }.merge()
    }

operator fun <R1, E1, R2 : R1, E2 : E1> Effect<R2, E2>.plus(otherEffect: Effect<R1, E1>) =
    this.with(otherEffect)

data class Effects<R1, R2, E1, E2>(
    private val inner: Effect<R1, E1>,
    private val outer: Effect<R2, E2>
) : Effect<R2, Either<E1, E2>>
        where R2 : R1 {

    override fun <A> wrap(f: (R2) -> A): (R2) -> Pair<Out<Either<E1, E2>, A>, R2> = run {
        @Suppress("UNCHECKED_CAST")
        val internalFunct = { r: R2 -> f(r) } as (R1) -> A
        val innerWrapped: (R1) -> Pair<Out<Either<E1, E2>, A>, R1> =
            inner.handleError { error: E1 -> Either.left<E1, E2>(error) }
                .wrap(internalFunct)
        val outerF =
            { rn: R2 ->
                val z = innerWrapped(rn)
                z.first
            }

        val outerWrapped: (R2) -> Pair<Out<Either<E1, E2>, Out<Either<E1, E2>, A>>, R2> = outer
            .handleError { error -> Either.right<E1, E2>(error) }
            .wrap(outerF)

        val result = { r: R2 ->
            val res = outerWrapped(r)
            val finalR = res.second
            // TODO - finalR or r?
            val called = res.first
            val x: Out<Either<E1, E2>, A> = called.flatMap { it }

            Pair(x, finalR)
        }
        result
    }

    companion object {
        fun <R1, R2 : R1, E1, E2> combine(outer: Effect<R1, E1>, inner: Effect<R2, E2>): Effect<R2, Either<E1, E2>> =
            Effects<R1, R2, E1, E2>(outer, inner)
    }
}

class NoEffect<R, E> : Effect<R, E> {
    override fun <A> wrap(f: (R) -> A): (R) -> Pair<Out<E, A>, R> =
        { r -> Pair(Out.right<E, A>(f(r)), r) }

    companion object {
        private val singleInstance = NoEffect<Any, Any>()

        @Suppress("UNCHECKED_CAST")
        fun <R, E> get() = singleInstance as NoEffect<R, E>
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <R, E> noEffect() = NoEffect.get<R, E>()

data class HandleErrorEffect<R, E, E1>(
    private val innerEffect: Effect<R, E>,
    private val handler: (E) -> E1
) : Effect<R, E1> {
    override fun <A> wrap(f: (R) -> A): (R) -> Pair<Out<E1, A>, R> = { r: R ->
        val result = innerEffect.wrap(f)(r)
        Pair(result.first.mapLeft(handler), result.second)
    }
}

fun <R, E> Effect<R, E>.anyError(): Effect<R, Any> = HandleErrorEffect(this) {
    foldErrors(it as Any)
}

// TODO tests
private fun foldErrors(e: Any): Any =
    when (e) {
        is Either<*, *> -> {
            e.mapLeft { foldErrors(it as Any) }
                .map { foldErrors(it as Any) }
                .merge()
        }
        else -> e
    }
