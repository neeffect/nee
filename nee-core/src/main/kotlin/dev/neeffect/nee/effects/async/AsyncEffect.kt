package dev.neeffect.nee.effects.async

import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.effects.utils.logger
import io.vavr.concurrent.Future
import io.vavr.concurrent.Promise
import io.vavr.control.Either
import io.vavr.control.Option
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

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

@Suppress("ReturnUnit")
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

class ExecutorExecutionContext(private val executor: Executor) : ExecutionContext, Logging {
    @Suppress("TooGenericExceptionCaught")
    override fun <T> execute(f: () -> T): Future<T> =
        Promise.make<T>(InPlaceExecutor).let { promise ->
            executor.execute {
                // LESSON not property handled exception
                try {
                    val result = f()
                    promise.success(result)
                } catch (e: Exception) {
                    // NO TEST
                    promise.failure(e)
                } catch (e: Throwable) {
                    logger().error("Unhandled throwable in executor", e)
                    promise.failure(e)
                }
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

    @Suppress("TooGenericExceptionCaught", "ThrowExpression")
    override fun <A> wrap(f: (R) -> A): (R) -> Pair<Out<Nothing, A>, R> =
        { r: R ->
            val asyncNmb = asyncCounter.getAndIncrement()

            Pair(run {
                val ec = r.findExecutionContext(this.localExecutionContext)
                logger().debug("initiated async ($asyncNmb)")
                val async = AsyncSupport.initiateAsync(r)
                val result = ec.execute {
                    logger().debug("started async ($asyncNmb)")
                    try {
                        f(r)
                    } catch (e: Throwable) {
                        logger().error("error in async handling", e)
                        throw e
                    } finally {
                        logger().debug("done async ($asyncNmb)")
                    }
                }
                Out.FutureOut(result.map {
                    Either.right<Nothing, A>(it.also {
                        logger().debug("cleaning async ($asyncNmb)")
                        async.closeAsync(r)
                    })
                })
            }, r)
        }

    companion object {
        private val asyncCounter = AtomicLong()
    }
}

class ThreadedExecutionContextProvider(threads: Int = 4)  : ExecutionContextProvider {
    val executor = Executors.newFixedThreadPool(threads)
    val executorUsingContext = ExecutorExecutionContext(executor)
    override fun findExecutionContext(local: Option<ExecutionContext>): ExecutionContext
    = local.getOrElse(executorUsingContext)

}
