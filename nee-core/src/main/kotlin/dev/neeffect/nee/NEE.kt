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
sealed class Nee<R, E, out A>(internal val effect: Effect<R, E>) {
    /**
     * Call Nee with given environment.
     *
     * @return function ready to apply business param
     */
    abstract fun perform(env: R): Out<E, A>

    abstract fun <B> map(f: (A) -> B): Nee<R, E, B>
    abstract fun <B> flatMap(f: (A) -> Nee<R, E, B>): Nee<R, E, B>

    @Suppress("UNCHECKED_CAST")
    fun anyError(): ANee<R, A> = this as ANee<R, A>

    companion object {
        fun <R, E, A> pure(a: A): Nee<R, E, A> =
            FNEE<R, E, A>(
                NoEffect.get(),
                { _: R -> Out.right(a) }
            )

        /**
        alias to pure
         */
        fun <R, E, A> success(a: A) = pure<R, E, A>(a)

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
            FNEE(NoEffect()) { value.mapLeft { e -> e } }
        }

        // TODO (is it needed?)
        fun <R, E, A> withError(effect: Effect<R, E>, func: (R) -> Out<E, A>): Nee<R, E, A> =
            FNEE(effect, func)

        fun <R, E, A> constWithError(effect: Effect<R, E>, func: (R) -> Out<E, A>): Nee<R, E, A> =
            FNEE(effect) { r: R -> func(r) }
    }
}

internal fun <T, T1> T.map(f: (T) -> T1) = f(this)
// CONTROVERSIAL

internal class FNEE<R, E, A>(
    effect: Effect<R, E>,
    private val func: (R) -> Out<E, A>
) : Nee<R, E, A>(effect) {

    private fun action() = effect.wrap(func)

    override fun perform(env: R): Out<E, A> = action()(env).first.flatMap { it }

    override fun <B> map(f: (A) -> B): Nee<R, E, B> =
        FNEE(effect) { r -> func(r).map(f) }

    /**
     *
     *
     */
    override fun <B> flatMap(f: (A) -> Nee<R, E, B>): Nee<R, E, B> = FMNEE(effect, func, f)
}

internal class FMNEE<R, E, A, A1>(
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

    override fun <B> flatMap(f: (A) -> Nee<R, E, B>): Nee<R, E, B> = FMNEE(effect, func, { a: A1 ->
        mapped(a).flatMap(f)
    })
}
