package dev.neeffect.nee

import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.toFuture
import io.vavr.concurrent.Promise
import io.vavr.control.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

// This is just preview/sketch how to integrate nee with coroutines
fun <R, E, A> Nee<R, E, A>.go(): suspend R.() -> A = this.let { nee ->
    {
        val r = this
        suspendCoroutine { continuation ->
            nee.perform(r).toFuture().onComplete { result ->
                result.forEach { either ->
                    continuation.resumeWith(Result.success(either.get()))
                }
            }
        }
    }
}

suspend fun <R, E, A> R.go(n: Nee<R, E, A>): A =
    n.perform(this).k()().get()

suspend fun <R, E, A> R.goSafe(n: Nee<R, E, A>): Either<E, out A> =
    n.perform(this).k()()

fun <T> runNee(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T):
        Nee<Any, Throwable, T> = run {
    val coroutine = NeeCoroutine<T>(context)
    val promise = Promise.make<Either<Throwable, T>>()
    block.startCoroutine(coroutine, object : Continuation<T> {
        override val context: CoroutineContext
            get() = context

        override fun resumeWith(result: Result<T>) {
            if (result.isSuccess) {
                promise.success(Either.right(result.getOrThrow()))
            } else {
                promise.success(Either.left(result.exceptionOrNull() ?: NullPointerException("unknown error")))
            }
        }
    })
    val future = promise.future()
    val out = Out.FutureOut(future)
    Nee.fromOut(out)
}

private class NeeCoroutine<T>(
    val parentContext: CoroutineContext
) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = parentContext
}

fun <E, A> Out<E, A>.k(): suspend () -> Either<E, A> =
    when (this) {
        is Out.InstantOut -> { -> this.v }
        is Out.FutureOut -> { -> this.futureVal.toCompletableFuture().await() }
    }
