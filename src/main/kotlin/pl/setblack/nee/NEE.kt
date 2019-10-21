package pl.setblack.nee

//import arrow.core.*
import io.vavr.control.Either
import java.sql.Connection
import java.sql.SQLException


//NEE - better name naive enterprise effects

sealed class NEE<R, E, P, A>(val effect: Effect<R, E>) {
    abstract fun perform(env: R): (P) -> Either<E, A>
    abstract fun <B> map(f: (A) -> B): NEE<R, E, P, B>
    abstract fun <B> flatMap(f: (A) -> NEE<R, E, P, B>): NEE<R, E, P, B>

    companion object {
        fun <A> pure(a: A): NEE<Any, Nothing, Nothing, A> =
            FNEE<Any, Nothing, Nothing, A>(NoEffect<Any, Nothing>()) { _ -> Either.right(a) }

        fun <R, E, P, A> pure(effect: Effect<R, E>, func: (R) -> (P) -> Either<E, A>): NEE<R, E, P, A> =
            FNEE(effect, func)
    }
}

internal class FNEE<R, E, P, A>(
    effect: Effect<R, E>,
    val func: (R) -> (P) -> Either<E, A>
) : NEE<R, E, P, A>(effect) {
    private fun action() = effect.wrap(func)
    override fun perform(env: R): (P) -> Either<E, A> = action()(env).first  //f(env)
    //fun wrap(eff: Effect<R, E>): BaseENIO<R, E, A> = BaseENIO(f, effs.plusElement(eff).k())
    override fun <B> map(f: (A) -> B): NEE<R, E, P, B> =
        FNEE(effect) { r -> { p: P -> func(r)(p).map(f) } }

    override fun <B> flatMap(f: (A) -> NEE<R, E, P, B>): NEE<R, E, P, B> {
        val f2 = { r: R ->
            { p: P ->
                val z = func(r)(p).map(f).flatMap { it.perform(r)(p) }
                z
            }
        }
        return FNEE(effect, f2)
    }
}

//class ENEE<R,E,A> (  effect: Effect<R, E>, e: E) : NEE<R,E,A>(effect) {
//
//}


interface JDBCProvider {
    fun getConnection(): Connection
}

class JDBCTx<R : JDBCProvider, E>(private val handler: (SQLException) -> E) : Effect<R, E> {
    override fun <A> wrap(f: (R) -> Either<E, A>): (R) -> Pair<Either<E, A>, R> = { provider ->
        val connection = provider.getConnection()
        connection.autoCommit = true
        try {
            val result = f(provider)
            connection.commit()
            Pair(result, provider)
        } catch (e: SQLException) {
            connection.rollback()
            Pair(Either.left(handler(e)), provider)
        }
    }
}


//fun dup() {
//    val f1 = businessFunc("irreg")
//    //val enterprisol = NEE<>
//}

//fun businessFunc(a: String) = { securityCtx: SecurityCtxProvider ->
//    { connection: JDBCProvider ->
//        TODO()
//    }
//}