package pl.setblack.nee

import io.vavr.Tuple2
import io.vavr.Tuple3
import io.vavr.control.Either
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.monitoring.TraceProvider

internal fun <R, E, P, A> extend(f: (R) -> (P) -> A) = { r: R ->
    val pfunc = f(r)
    if (r is TraceProvider<*>) {
        r.getTrace().guessPlace(pfunc)
    }
    { p: P -> Out.right<E, A>(pfunc(p)) }
}

//was extendP
internal fun <R, E, A> constP(f: (R) -> A) = { r: R ->
    if (r is TraceProvider<*>) {
        r.getTrace().guessPlace(f)
    }
    { _: Unit -> Out.right<E, A>(f(r)) }
}

internal fun <R, E, P, A> constR(f: (P) -> A) = { r: R ->
    if (r is TraceProvider<*>) {
        r.getTrace().guessPlace(f)
    }
    { p: P -> Out.right<E, A>(f(p)) }
}

internal fun <P, A> ignoreR(f: (P) -> A) = { r: Any ->
    if (r is TraceProvider<*>) {
        r.getTrace().guessPlace(f)
    }
    { p: P -> f(p) }
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


interface Logging

inline fun <reified T : Logging> T.logger(): Logger =
    getLogger(T::class.java)

