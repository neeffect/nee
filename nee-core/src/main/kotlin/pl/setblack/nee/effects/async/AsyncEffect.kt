package pl.setblack.nee.effects.async

import io.vavr.concurrent.Future
import io.vavr.concurrent.Promise
import io.vavr.control.Either
import io.vavr.control.Option
import pl.setblack.nee.Effect
import pl.setblack.nee.effects.Fe
import java.util.concurrent.Executor
import kotlin.system.exitProcess

interface ExecutionContext {
    fun <T> execute(f: () -> T): Future<T>
}

interface ExecutionContextProvider {
    fun findExceutionContext(local: Option<ExecutionContext>): ExecutionContext
}

class SyncExecutionContext : ExecutionContext {
    override fun <T> execute(f: () -> T): Future<T> =
        Future.successful(f())

}

object InPlaceExecutor : Executor {
    override fun execute(command: Runnable)  = command.run()
}

/* maybe we do not need this radical one
object NoGoExecutor : Executor {
    override fun execute(command: Runnable) {
        System.err.println("an idiot called NoGoExecutor")
        Thread.dumpStack()
        exitProcess(2)
    }
}
 */

class ExecutorExecutionContext(private val executor: Executor) : ExecutionContext {
    override fun <T> execute(f: () -> T): Future<T> =
        Promise.make<T>(InPlaceExecutor).let { promise ->
            executor.execute {
                promise.success(f())
            }
            promise.future()
        }
}

class ECProvider(private val ectx: ExecutionContext, private val localWins: Boolean = true) : ExecutionContextProvider {
    override fun findExceutionContext(local: Option<ExecutionContext>): ExecutionContext =
        local.map { localCtx ->
            if (localWins) {
                localCtx
            } else {
                ectx
            }
        }.getOrElse(ectx)
}

class AsyncEffect<R : ExecutionContextProvider>(
    val localExecutionContext: Option<ExecutionContext> = Option.none()
) : Effect<R, Nothing> {
    override fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Fe<Nothing, A>, R> =
        { r: R ->
            Pair({ p: P ->
                val ec = r.findExceutionContext(this.localExecutionContext)
                val result = ec.execute {
                    f(r)(p)
                }
                Fe.FutureFe(result.map { Either.right<Nothing, A>(it) })
            }, r)
        }
}