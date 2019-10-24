package pl.setblack.nee

import io.vavr.Tuple2
import io.vavr.Tuple3
import io.vavr.control.Either
import pl.setblack.nee.effects.Fe
import pl.setblack.nee.effects.monitoring.TraceProvider

internal fun <R, E, P, A> extend(f: (R) -> (P) -> A) = { r: R ->
    val pfunc = f(r)
    if (r is TraceProvider<*>) {
        r.getTrace().guessPlace(pfunc)
    }
    { p: P -> Fe.right<E, A>(pfunc(p)) }
}

internal fun <P, A> extendR(f: (P) -> A) = { r: Any ->
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


fun a() {
    val xf = { x: Int -> { a: Int, b: Float -> a * b } }
    val xfp = tupled(xf)

}