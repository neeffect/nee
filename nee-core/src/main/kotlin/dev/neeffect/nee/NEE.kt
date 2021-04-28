/**
Copyright 2019 Jarek Ratajski

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package dev.neeffect.nee

import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.flatMap
import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.utils.trace

/**
 * Nee returning Any error.
 *
 * It is bad but practical. Having common (Any) error type
 * allows for monad binding.
 * I consider it less ugly then Error inheriting from Exception / Throwable
 * //CONTROVERSIAL
 */
typealias ANee<R, A> = Nee<R, Any, A>

typealias IO<A> = Nee<Any, Nothing, A>

/**
 * Nee monad.
 *
 *  Naive enterprise effects
 *  Wraps business  function inside some "effects".
 *
 *
 *  @param R environment to run function on
 *  @param P param of function (business)
 *  //CONTROVERSIAL P is only added to handle caching
 *   - causes lot of complexity, does not give much
 *  @param E error that can happen (because of effects)
 *  @param A expected result type
 */
sealed class Nee<in R, out E, out A>(internal val effect: Effect<@UnsafeVariance R, @UnsafeVariance E>) {
    /**
     * Call Nee with given environment.
     *
     * @return function ready to apply business param
     */
    abstract fun perform(env: R): Out<E, A>

    abstract fun <B> map(f: (A) -> B): Nee<R, E, B>
    abstract fun <B, R1 : R, E2 : @UnsafeVariance E> flatMap(f: (A) -> Nee<R1, E2, B>): Nee<R1, E, B>

    @Suppress("UNCHECKED_CAST")
    fun anyError(): ANee<R, A> = this as ANee<R, A>

    fun runUnsafe(env: R): A = perform(env).get()

    companion object {
        fun <R, E, A> pure(a: () -> A): Nee<R, E, A> =
            FNEE<R, E, A>(
                NoEffect.get(),
                { _: R -> Out.right(a()) }
            )

        /**
        alias to pure
         */
        fun <A> success(a: () -> A) = pure<Any, Nothing, A>(a)

        fun <E> fail(e: E): Nee<Any, E, Nothing> = FNEE(NoEffect()) { Out.left(e) }

        fun <R, E> failOnly(e: E): Nee<R, E, Nothing> = FNEE(NoEffect()) { Out.left(e) }

        /**
         * Same as pure, but adds tracing.
         */
        fun <R, E, A : Any> constR(effect: Effect<R, E>, value: A): Nee<R, E, A> =
            FNEE(effect, dev.neeffect.nee.effects.utils.constR(value))

        /**
        from function - adds tracing.
         */
        fun <R, E, A> with(effect: Effect<R, E>, func: (R) -> A): Nee<R, E, A> =
            FNEE(effect, trace(func))

        fun <R, E, A, E1 : E> flatOut(f: Nee<R, E, Out<E1, A>>) = f.flatMap { value ->
            FNEE(NoEffect<R, E>()) { value.mapLeft { e -> e } }
        }

        fun <A, E> fromOut(out: Out<E, A>): Nee<Any, E, A> =
            FNEE(NoEffect()) { out }

        // TODO (is it needed?)
        fun <R, E, A> withError(effect: Effect<R, E>, func: (R) -> Out<E, A>): Nee<R, E, A> =
            FNEE(effect, func)

        fun <R, E, A> constWithError(effect: Effect<R, E>, func: (R) -> Out<E, A>): Nee<R, E, A> =
            FNEE(effect) { r: R -> func(r) }
    }
}

internal fun <T, T1> T.map(f: (T) -> T1) = f(this)
// CONTROVERSIAL

@Suppress("UNCHECKED_CAST")
internal class FNEE<in R, E, out A>(
    effect: Effect<R, E>,
    private val func: (R) -> Out<E, A>
) : Nee<R, E, A>(effect) {

    private fun action(): (R) -> Pair<Out<E, Out<E, A>>, R> = effect.wrap(func)

    override fun perform(env: R): Out<E, A> = action()(env).first.flatMap { it }

    override fun <B> map(f: (A) -> B): Nee<R, E, B> =
        FNEE(effect) { r -> func(r).map(f) }

    override fun <B, R1 : R, E2 : E> flatMap(f: (A) -> Nee<R1, E2, B>): Nee<R1, E, B> =
        FMNEE<R1, E, B, A>(effect as Effect<R1, E>, func, f)
}

@Suppress("UNCHECKED_CAST") // only needed because invariance in Effect
internal class FMNEE<R, E, out A, out A1>(
    effect: Effect<R, E>,
    private val func: (R) -> Out<E, A1>,
    private val mapped: (A1) -> Nee<R, E, A>
) : Nee<R, E, A>(effect) {

    override fun perform(env: R): Out<E, A> = { r: R ->
        val res1 = func(r).map(mapped)
            .flatMap {
                it.perform(r)
            }
        res1
    }.let { newF ->
        effect.wrap(newF)(env).first.flatMap { it }
    }

    override fun <B> map(f: (A) -> B): Nee<R, E, B> = FMNEE(effect, func, { a: A1 ->
        mapped(a).map(f)
    })

    override fun <B, R1 : R, E2 : E> flatMap(f: (A) -> Nee<R1, E2, B>): Nee<R1, E, B> =
        FMNEE<R1, E, B, A1>(effect as Effect<R1, E>, func, { a: A1 ->
            mapped(a).flatMap(f)
        })
}

@Suppress("UNCHECKED_CAST")
fun <R, E, A> Nee<R, Nothing, A>.withErrorType() = this as Nee<R, E, A>
