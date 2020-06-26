package pl.setblack.nee.effects.async

import io.vavr.collection.Seq
import io.vavr.collection.List
import pl.setblack.nee.effects.env.ResourceId
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicReference


interface AsyncSupport<R> {
    fun asyncStack(): AsyncStack<R>

    fun setAsyncStack(newState: AsyncStack<R>): Unit

    companion object {
        val asyncResouce = ResourceId(AsyncStack::class)

        @Suppress("UNCHECKED_CAST")
        fun <R> doOnCleanUp(env: R, action: AsyncClosingAction<R>) = when (env) {
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
                    //strange thing happened here
                    when (stack) {
                        is ActiveAsynStack<*> -> {
                            val newStack = stack as ActiveAsynStack<R>
                            val res = newStack.closeAsync(env)
                            async.setAsyncStack(res.first)
                            res.second
                        }
                        else -> {
                            //todo maybe it is not totally error
                            throw IllegalStateException("no matching active async stack")
                        }
                    }
                }
            }
            else ->
                    doNothing(env)
        }
}

class SthToClean<R>(val asyncClean: DirtyAsyncStack<R>) {
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
                            async.setAsyncStack(newStack.first)//todo - think if possible
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

fun <R> doNothing(env:R) = env

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


sealed class AsyncStack<R>(val actions: Seq<AsyncClosingAction<R>> = List.empty()) {
    fun doOnCleanUp(action: AsyncClosingAction<R>): DirtyAsyncStack<R> = DirtyAsyncStack(this, actions.prepend(action))

    fun enterAsync(): ActiveAsynStack<R> = ActiveAsynStack(this, actions)

    protected fun performActions(env: R) = actions.foldLeft(env) { r, action ->
        action.onClose(r)
    }
}

class CleanAsyncStack<R> : AsyncStack<R>()

class DirtyAsyncStack<R>(val parent: AsyncStack<R>, actions: Seq<AsyncClosingAction<R>>) : AsyncStack<R>(actions) {
    fun cleanUp(env: R): Pair<AsyncStack<R>, R> = Pair(parent, performActions(env))
}

class ActiveAsynStack<R>(val parent: AsyncStack<R>, actions: Seq<AsyncClosingAction<R>>) : AsyncStack<R>(actions) {
    fun closeAsync(env: R): Pair<AsyncStack<R>, R> = Pair(parent, performActions(env))
}


interface AsyncClosingAction<R> {
    fun onClose(env: R): R

    fun onError(env: R, t: Throwable): R
}

abstract class AsyncClose<R> : AsyncClosingAction<R> {
    override fun onError(env: R, t: Throwable): R = env
}

fun <R> AsyncStack<R>.onClose(f: (R) -> R): DirtyAsyncStack<R> = this.doOnCleanUp(
    object : AsyncClose<R>() {
        override fun onClose(env: R): R = f(env)

    }
)


class AsyncWrapper<R>(
    private val state: AtomicReference<AsyncStack<R>> = AtomicReference(CleanAsyncStack())
) : AsyncSupport<R> {
    override fun asyncStack(): AsyncStack<R> = state.get()

    override fun setAsyncStack(newState: AsyncStack<R>) = state.set(newState)
}
