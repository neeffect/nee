package dev.neeffect.nee.effects.async

import io.vavr.collection.List
import io.vavr.collection.Seq
import dev.neeffect.nee.effects.env.ResourceId
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.effects.utils.logger
import java.util.concurrent.atomic.AtomicReference

/**
 * Marks  environment supporting async operations with post cleaning.
 */
interface AsyncSupport<R> {
    fun asyncStack(): AsyncStack<R>

    fun setAsyncStack(newState: AsyncStack<R>): Unit

    companion object {
        val asyncResouce = ResourceId(AsyncStack::class)

        @Suppress("UNCHECKED_CAST")
        internal fun <R> doOnCleanUp(env: R, action: AsyncClosingAction<R>) = when (env) {
            is AsyncSupport<*> -> (env as AsyncSupport<R>).let { async ->
                val stack = async.asyncStack()
                val newStack = stack.doOnCleanUp(action)
                async.setAsyncStack(newStack)
                SthToClean(newStack)
            }
            else -> {
                val stack = CleanAsyncStack<R>()
                val newStack = stack.doOnCleanUp(action)
                SthToClean(newStack)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun <R> initiateAsync(env: R) =
            when (env) {
                is AsyncSupport<*> -> {
                    val async = (env as AsyncSupport<R>)
                    val stack = async.asyncStack()
                    val asyncStack = stack.enterAsync()
                    async.setAsyncStack(asyncStack)
                    ActiveAsyncClose(asyncStack)
                }
                else -> ActiveAsyncClose(CleanAsyncStack<R>().enterAsync())
            }
    }
}

/**
 * Used to wrap async code.
 */
class ActiveAsyncClose<R>(private val asyncClean: ActiveAsynStack<R>) {
    @Suppress("UNCHECKED_CAST")
    fun closeAsync(env: R) =
        when (env) {
            is AsyncSupport<*> -> {
                val async = env as AsyncSupport<R>
                val stack = async.asyncStack()
                if (stack == asyncClean) {
                    val res = asyncClean.closeAsync(env)
                    async.setAsyncStack(res.first)
                    res.second
                } else {
                    doNothing(env)
                }
            }
            else ->
                doNothing(env)
        }
}

internal class SthToClean<R>(val asyncClean: DirtyAsyncStack<R>) {
    @Suppress("UNCHECKED_CAST")
    fun cleanUp(env: R) =
        when (env) {
            is AsyncSupport<*> -> {
                val async = env as AsyncSupport<R>
                val stack = env.asyncStack()
                if (stack != asyncClean) {
                    //someone messed in the middle
                    when (stack) {
                        is DirtyAsyncStack<R> -> {
                            //TODO this is strange
                            val newStack = stack.cleanUp(env)
                            async.setAsyncStack(newStack.first)
                            newStack.second
                        }
                        else -> {
                            doNothing(env)
                        }
                    }
                } else {
                    val newStack = asyncClean.cleanUp(env)
                    async.setAsyncStack(newStack.first)
                    newStack.second
                }
            }
            else -> asyncClean.cleanUp(env).second
        }
}

fun <R> doNothing(env: R) = env

fun <R, T> executeAsyncCleaning(env: R, action: () -> T, cleanAction: (R) -> R): T {
    val closingAction = object : AsyncClose<R>() {
        override fun onClose(env: R): R = cleanAction(env)
    }
    val cleaning = AsyncSupport.doOnCleanUp(env, closingAction)
    val result = try {
        action()
    } finally {
        cleaning.cleanUp(env)
    }
    return result
}

/**
 * Registry of async cleaning operations.
 */
sealed class AsyncStack<R>(val actions: Seq<AsyncClosingAction<R>> = List.empty()) {
    fun doOnCleanUp(action: AsyncClosingAction<R>): DirtyAsyncStack<R> = DirtyAsyncStack(this, List.of(action))

    open fun enterAsync(): ActiveAsynStack<R> = ActiveAsynStack(this.empty(), actions)

    internal open fun empty() = this

    protected fun performActions(env: R) = actions.foldLeft(env) { r, action ->
        action.onClose(r)
    }
}

/**
 * Empty registry.
 */
class CleanAsyncStack<R> : AsyncStack<R>()

/**
 * Something is registered.
 */
class DirtyAsyncStack<R>(val parent: AsyncStack<R>, actions: Seq<AsyncClosingAction<R>>) : AsyncStack<R>(actions) {
    fun cleanUp(env: R): Pair<AsyncStack<R>, R> = Pair(parent, performActions(env))

    override fun enterAsync(): ActiveAsynStack<R> = ActiveAsynStack(this.parent, actions)

    override fun empty(): AsyncStack<R> = DirtyAsyncStack(parent, List.empty())
}

/**
 * Ongoing async process.
 */
class ActiveAsynStack<R>(val parent: AsyncStack<R>, actions: Seq<AsyncClosingAction<R>>) : AsyncStack<R>(actions) {
    fun closeAsync(env: R): Pair<AsyncStack<R>, R> =
        Pair(parent, performActions(env))

    override fun empty(): AsyncStack<R> = ActiveAsynStack(parent, List.empty())
}

/**
 * Action that performs clean
 */
interface AsyncClosingAction<R> {
    fun onClose(env: R): R

    fun onError(env: R, t: Throwable): R
}


internal abstract class AsyncClose<R> : AsyncClosingAction<R> {
    override fun onError(env: R, t: Throwable): R = TODO()
}

fun <R> AsyncStack<R>.onClose(f: (R) -> R): DirtyAsyncStack<R> = this.doOnCleanUp(
    object : AsyncClose<R>() {
        override fun onClose(env: R): R = f(env)
    }
)

/**
 * Keeps async status as atomic reference.
 */
class AsyncEnvWrapper<R>(
    private val state: AtomicReference<AsyncStack<R>> = AtomicReference(CleanAsyncStack())
) : AsyncSupport<R>, Logging {
    override fun asyncStack(): AsyncStack<R> = state.get()

    override fun setAsyncStack(newState: AsyncStack<R>) = state.set(newState).also {
        logger().debug("assigned state: $newState")
    }
}
