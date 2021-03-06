package dev.neeffect.nee.effects.test

import dev.neeffect.nee.effects.Out
import io.vavr.control.Either

fun <E, A> Out<E, A>.get(): A = when (this) {
    is Out.InstantOut -> this.v.get()
    is Out.FutureOut -> this.futureVal.get().get()
}

fun <E, A> Out<E, A>.getLeft(): E = when (this) {
    is Out.InstantOut -> this.v.left
    is Out.FutureOut -> this.futureVal.get().left
}

fun <E, A> Out<E, A>.getAny(): Either<E, A> = when (this) {
    is Out.InstantOut -> this.v
    is Out.FutureOut -> this.futureVal.get()
}
