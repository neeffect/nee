package pl.setblack.nee.effects

fun <E,A> Out<E,A>.get() : A = when (this) {
    is Out.InstantOut -> this.v.get()
    is Out.FutureOut -> this.futureVal.get().get()
}

fun <E,A> Out<E,A>.getLeft() : E = when (this) {
    is Out.InstantOut -> this.v.left
    is Out.FutureOut -> this.futureVal.get().left
}

