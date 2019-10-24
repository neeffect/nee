package pl.setblack.nee.effects

fun <E,A> Fe<E,A>.get() : A = when (this) {
    is Fe.InstantFe -> this.v.get()
    is Fe.FutureFe -> this.futureVal.get().get()
}

fun <E,A> Fe<E,A>.getLeft() : E = when (this) {
    is Fe.InstantFe -> this.v.left
    is Fe.FutureFe -> this.futureVal.get().left
}

