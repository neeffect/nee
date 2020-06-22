package pl.setblack.nee.effects.async

import io.vavr.collection.Seq
import io.vavr.collection.List

sealed class AsyncStack<R>(val actions: Seq<AsyncClosingAction<R>> = List.empty()) {
        fun doOnCleanUp( action : AsyncClosingAction<R>) : DirtyAsyncStack<R> = DirtyAsyncStack(this, actions.prepend(action))

        fun enterAsync() : ActiveAsynStack<R> = ActiveAsynStack(this, actions)

        protected fun performActions(env: R) = actions.foldLeft(env){r, action ->
            action.onClose(r)
        }
}

class CleanAsyncStack<R> : AsyncStack<R>()

class DirtyAsyncStack<R>(val parent:AsyncStack<R>, actions: Seq<AsyncClosingAction<R>>)  : AsyncStack<R>(actions) {
    fun cleanUp(env: R) : Pair<AsyncStack<R>, R> =  Pair(parent, performActions(env))

}

class ActiveAsynStack<R>(val parent:AsyncStack<R>, actions: Seq<AsyncClosingAction<R>>) : AsyncStack<R>(actions) {

    fun closeAsync(env: R) : Pair<AsyncStack<R>,R> = Pair(parent, performActions(env))
}



interface AsyncClosingAction<R>{
    fun onClose(env: R) : R

    fun onError(env: R, t:Throwable) : R
}

abstract class AsyncClose<R> : AsyncClosingAction<R> {
    override fun onError(env: R, t: Throwable): R  = env
}

fun <R> AsyncStack<R>.onClose( f : (R) -> R ): DirtyAsyncStack<R> =  this.doOnCleanUp(
    object : AsyncClose<R>() {
        override fun onClose(env: R): R = f(env)

    }
)