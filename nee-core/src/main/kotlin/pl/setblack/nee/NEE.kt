package pl.setblack.nee

//import arrow.core.*
import io.vavr.control.Either
import pl.setblack.nee.effects.Fe
import pl.setblack.nee.effects.monitoring.TraceProvider
import pl.setblack.nee.effects.monitoring.TraceResource


//NEE - better name naive enterprise effects

sealed class NEE<R, E, P, A>(val effect: Effect<R, E>) {
    abstract fun perform(env: R): (P) -> Fe<E, A>
    abstract fun <B> map(f: (A) -> B): NEE<R, E, P, B>
    abstract fun <B> flatMap(f: (A) -> NEE<R, E, P, B>): NEE<R, E, P, B>

    companion object {
        fun <A> pure(a: A): NEE<Any, Nothing, Nothing, A> =
            FNEE<Any, Nothing, Nothing, A>(NoEffect<Any, Nothing>(), extend({ _: Any -> { _: Nothing -> a } }))

        fun <R, E, P, A> pure(effect: Effect<R, E>, func: (R) -> (P) -> A): NEE<R, E, P, A> =
            FNEE(effect, extend(func))

    }
}

internal fun <T, T1> T.map(f: (T) -> T1) = f(this) //TODO watch this


internal class FNEE<R, E, P, A>(
    effect: Effect<R, E>,
    private val func: (R) -> (P) -> Fe<E, A>
) : NEE<R, E, P, A>(effect) {

    //constructor(effect: Effect<R, E>, f : (R)->(P)->A) : this(effect, {r:R-> {p:P-> Either.right<E,A>(f(r)(p))}} )

    private fun action() = effect.wrap(func)
    override fun perform(env: R): (P) -> Fe<E, A> = { p: P -> action()(env).first(p).flatMap { it } }//f(env)
    //fun wrap(eff: Effect<R, E>): BaseENIO<R, E, A> = BaseENIO(f, effs.plusElement(eff).k())
    override fun <B> map(f: (A) -> B): NEE<R, E, P, B> =
        FNEE(effect) { r -> { p: P -> func(r)(p).map(f) } }

    override fun <B> flatMap(f: (A) -> NEE<R, E, P, B>): NEE<R, E, P, B> {
        val f2 = { r: R ->
            { p: P ->
                val z = func(r)(p).map(f)
                z.flatMap { it.perform(r)(p) }
            }
        }
        return FNEE(effect, f2)
    }
}


//class ENEE<R,E,A> (  effect: Effect<R, E>, e: E) : NEE<R,E,A>(effect) {
//
//}


//fun dup() {
//    val f1 = businessFunc("irreg")
//    //val enterprisol = NEE<>
//}

//fun businessFunc(a: String) = { securityCtx: SecurityCtxProvider ->
//    { connection: JDBCProvider ->
//        TODO()
//    }
//}