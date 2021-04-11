package dev.neeffect.nee.atomic

import dev.neeffect.nee.IO
import dev.neeffect.nee.Nee
import java.util.concurrent.atomic.AtomicReference

@Suppress("UNUSED_PARAMETER")
class AtomicRef<A>(value: A) {

    private val internal: AtomicReference<A> = AtomicReference(value)

    fun get(): IO<A> = Nee.pure {
        internal.get()
    }

    fun set(a: A): IO<Unit> = Nee.pure {
        internal.set(a)
    }

    fun getAndSet(a: A): IO<A> = Nee.pure {
        internal.getAndSet(a)
    }

    fun update(f: (A) -> A): IO<Unit> = Nee.pure {
        internal.updateAndGet(f)
    }

    fun getAndUpdate(f: (A) -> A): IO<A> = Nee.pure {
        internal.getAndUpdate(f)
    }

    fun updateAndGet(f: (A) -> A): IO<A> = Nee.pure {
        internal.updateAndGet(f)
    }

    fun <B> modify(f: (A) -> Pair<A, B>): IO<B> = modifyGet(f).map(Pair<A, B>::second)

    fun <B> modifyGet(f: (A) -> Pair<A, B>): IO<Pair<A, B>> = Nee.pure {
        modifyImpure(f)
    }

    fun tryUpdate(f: (A) -> A): IO<Boolean> = Nee.pure {
        val start = internal.get()
        val result = f(start)
        internal.compareAndSet(start, result)
    }

    private fun <B> modifyImpure(f: (A) -> Pair<A, B>): Pair<A, B> = run {
        val start = internal.get()
        val result = f(start)
        if (internal.compareAndSet(start, result.first)) {
            result
        } else {
            modifyImpure(f)
        }
    }
}
