package dev.neeffect.nee.effects.utils

import io.vavr.Tuple2
import io.vavr.Tuple3
import io.vavr.control.Either
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.monitoring.CodeNameFinder.guessCodePlaceName
import dev.neeffect.nee.effects.monitoring.TraceProvider

internal fun <R, E, P, A> extend(f: (R) -> (P) -> A) = guessCodePlaceName(2).let { placeName ->
    { r: R ->
        { p: P ->
            Out.right<E, A>(f(r)(p)).also {
                if (r is TraceProvider<*>) {
                    r.getTrace().putGuessedPlace(placeName, f)
                }
            }
        }
    }
}

//was extendP
internal fun <R, E, A> constP(f: (R) -> A) = guessCodePlaceName(2).let { placeName ->
    { r: R ->
        { _: Unit ->
            Out.right<E, A>(f(r)).also {
                if (r is TraceProvider<*>) {
                    r.getTrace().putGuessedPlace(placeName, f)
                }
            }
        }

    }
}

internal fun <R, E, P, A> constR(f: (P) -> A) = guessCodePlaceName(2).let { placeName ->
    { r: R ->
        { p: P ->
            Out.right<E, A>(f(p)).also {
                if (r is TraceProvider<*>) {
                    r.getTrace().putGuessedPlace(placeName, f)
                }
            }
        }
    }
}

internal fun <P, A> ignoreR(f: (P) -> A) = guessCodePlaceName(2).let { placeName ->
    { r: Any ->
        { p: P ->
            f(p).also {
                if (r is TraceProvider<*>) {
                    r.getTrace().putGuessedPlace(placeName, f)
                }
            }
        }
    }
}

fun <T> Either<T, T>.merge() = getOrElseGet { it }

fun <ENV, A, B, R> tupled2(f: (ENV) -> (A, B) -> R) =
    { env: ENV ->
        { p: Tuple2<A, B> ->
            f(env)(p._1, p._2)
        }
    }

fun <ENV, A, B, R> tupled(f: (ENV) -> (A, B) -> R) = tupled2(f)

fun <ENV, A, B, C, R> tupled3(f: (ENV) -> (A, B, C) -> R) =
    { env: ENV ->
        { p: Tuple3<A, B, C> ->
            f(env)(p._1, p._2, p._3)
        }
    }


/**
 * Marks invalid function (expected to not be called).
 */
fun invalid(): Nothing = throw NotImplementedError()
