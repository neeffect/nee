package pl.setblack.nee

import pl.setblack.nee.effects.Out

typealias UNee<R,E,A> = Nee<R,E,Unit,A>

typealias ANee<R,P,A> = Nee<R,Any,P, A>

typealias UANee<R,A> = Nee<R, Any, Unit,A>

//NEE - better name naive enterprise effects

sealed class Nee<R, E, P, A>(val effect: Effect<R, E>) {
    abstract fun perform(env: R): (P) -> Out<E, A>
    abstract fun <B> map(f: (A) -> B): Nee<R, E, P, B>
    abstract fun <B> flatMap(f: (A) -> Nee<R, E, P, B>): Nee<R, E, P, B>
    abstract fun  constP(): (P) -> Nee<R, E, Unit, A>

    fun anyError() : ANee<R, P, A> = this as ANee<R,P,A>

    companion object {
        fun <A> pure(a: A): Nee<Any, Nothing, Nothing, A> =
            FNEE<Any, Nothing, Nothing, A>(NoEffect<Any, Nothing>(), extend({ _: Any -> { _: Nothing -> a } }))

        fun <R, E, P, A> constR(effect: Effect<R, E>, func: (P) -> A): Nee<R, E, P, A> =
            FNEE(effect, constR(func))

        fun <R, E, A> constP(effect: Effect<R, E>, func: (R) -> A): Nee<R, E, Unit, A> =
            FNEE(effect, constP(func))

        fun <R, E, P, A> pure(effect: Effect<R, E>, func: (R) -> (P) -> A): Nee<R, E, P, A> =
            FNEE(effect, extend(func))

    }
}

internal fun <T, T1> T.map(f: (T) -> T1) = f(this) //TODO watch this


internal class FNEE<R, E, P, A>(
    effect: Effect<R, E>,
    private val func: (R) -> (P) -> Out<E, A>
) : Nee<R, E, P, A>(effect) {

    //constructor(effect: Effect<R, E>, f : (R)->(P)->A) : this(effect, {r:R-> {p:P-> Either.right<E,A>(f(r)(p))}} )

    private fun action() = effect.wrap(func)
    override fun perform(env: R): (P) -> Out<E, A> = { p: P -> action()(env).first(p).flatMap { it } }//f(env)
    //fun wrap(eff: Effect<R, E>): BaseENIO<R, E, A> = BaseENIO(f, effs.plusElement(eff).k())
    override fun <B> map(f: (A) -> B): Nee<R, E, P, B> =
        FNEE(effect) { r -> { p: P -> func(r)(p).map(f) } }

    override fun <B> flatMap(f: (A) -> Nee<R, E, P, B>): Nee<R, E, P, B> {
        val f2 = { r: R ->
            { p: P ->
                val z = func(r)(p).map(f)
                z.flatMap { it.perform(r)(p) }
            }
        }
        return FNEE(effect, f2)
    }

    override fun  constP(): (P) -> Nee<R, E, Unit, A> = { p: P ->
        val f2 = { r: R ->
            { _: Unit ->
                this.perform(r)(p)
            }
        }
        FNEE(effect, f2)
    }

}

