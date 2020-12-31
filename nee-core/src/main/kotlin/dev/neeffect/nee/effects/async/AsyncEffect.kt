package dev.neeffect.nee.effects.async

import io.vavr.concurrent.Future
import io.vavr.concurrent.Promise
import io.vavr.control.Either
import io.vavr.control.Option
import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.effects.utils.logger
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.Executor

/**
 * Technical interface for threading model.
 *
 * Allows intrhead or real async execution.
 */
interface ExecutionContext {
    fun <T> execute(f: () -> T): Future<T>
}

/**
 * Provider of execution context.
 */
interface ExecutionContextProvider {
    /**
     * Find correct execution context.
     *
     * Implementation may choose to allow overriding by local.
     */
    fun findExecutionContext(local: Option<ExecutionContext>): ExecutionContext
}

/**
 * In thread execution (immediate).
 */
class SyncExecutionContext : ExecutionContext {
    override fun <T> execute(f: () -> T): Future<T> =
        Future.successful(f())
}

object InPlaceExecutor : Executor {
    override fun execute(command: Runnable) = command.run()
}

/* maybe we do not need this radical one
object NoGoExecutor : Executor {
    override fun execute(command: Runnable) {
        System.err.println("someone called NoGoExecutor")
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
    override fun findExecutionContext(local: Option<ExecutionContext>): ExecutionContext =
        local.map { localCtx ->
            if (localWins) {
                localCtx
            } else {
                ectx
            }
        }.getOrElse(ectx)
}

/**
 * Aynchority effect.
 *
 * Local execution context might be allowed.
 */
class AsyncEffect<R : ExecutionContextProvider>(
    val localExecutionContext: Option<ExecutionContext> = Option.none()
) : Effect<R, Nothing>, Logging {
    override fun <A> wrap(f: (R) -> A): (R) -> Pair< Out<Nothing, A>, R> =
        { r: R ->


            Pair(run {
                val ec = r.findExecutionContext(this.localExecutionContext)
                val async = AsyncSupport.initiateAsync(r)
                val result = ec.execute {
                    try {
                        f(r)
                    } catch (e: Exception) {
                        logger().error("error in async handling", e)
                        throw RuntimeException(e)
                    }
                }
                Out.FutureOut(result.map {
                    Either.right<Nothing, A>(it.also {
                        async.closeAsync(r)
                    })
                })
            }, r)
        }
}
