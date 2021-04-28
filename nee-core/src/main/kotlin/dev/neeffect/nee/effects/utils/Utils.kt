package dev.neeffect.nee.effects.utils

import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.monitoring.CodeNameFinder.guessCodePlaceName
import dev.neeffect.nee.effects.monitoring.TraceProvider
import io.vavr.Tuple2
import io.vavr.Tuple3
import io.vavr.control.Either

@Suppress("BranchStatement")
internal fun <R, E, A> trace(f: (R) -> A) = guessCodePlaceName(2).let { placeName ->
    { r: R ->
        Out.right<E, A>(f(r)).also {
            if (r is TraceProvider<*>) {
                r.getTrace().putGuessedPlace(placeName, f)
            }
        }
    }
}

@Suppress("BranchStatement")
internal fun <R, E, A : Any> constR(f: A) = guessCodePlaceName(2).let { placeName ->
    { r: R ->
        Out.right<E, A>(f).also {
            if (r is TraceProvider<*>) {
                r.getTrace().putGuessedPlace(placeName, f)
            }
        }
    }
}

// TODO - this is for lazy - rename it
@Suppress("BranchStatement")
internal fun <A : Any> ignoreR(f: () -> A) = guessCodePlaceName(2).let { placeName ->
    { r: Any ->
        f().also {
            if (r is TraceProvider<*>) {
                r.getTrace().putGuessedPlace(placeName, f)
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
@Suppress("ThrowExpression")
fun invalid(): Nothing = throw NotImplementedError()
