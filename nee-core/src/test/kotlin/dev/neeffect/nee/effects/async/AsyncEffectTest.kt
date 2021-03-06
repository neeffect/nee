package dev.neeffect.nee.effects.async

import dev.neeffect.nee.Nee
import dev.neeffect.nee.effects.utils.ignoreR
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vavr.collection.List
import io.vavr.concurrent.Future
import io.vavr.concurrent.Promise
import io.vavr.control.Option
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AsyncEffectTest : DescribeSpec({
    describe("async context") {
        val controllableExecutionContext = ControllableExecutionContext()
        val ecProvider = ECProvider(controllableExecutionContext)
        val eff = AsyncEffect<ECProvider>()
        describe("test function") {
            val runned = AtomicBoolean(false)
            val testFunction = { runned.set(true) }
            val async = Nee.Companion.with(eff, ignoreR(testFunction))
            async.perform(ecProvider)

            it("does not run before executor calls") {
                runned.get() shouldBe false
            }
            it("runs after async trigerred") {
                controllableExecutionContext.runSingle()
                runned.get() shouldBe true
            }
        }
        describe("with local ec") {
            val localEC = ControllableExecutionContext()
            //val localProvider = ECProvider(controllableExecutionContext)
            val localEff = AsyncEffect<ECProvider>(Option.some(localEC))
            val runned = AtomicBoolean(false)
            val testFunction = { runned.set(true) }
            val async = Nee.Companion.with(
                localEff,
                ignoreR(testFunction)
            )
            it("will not run  on global") {
                async.perform(ecProvider)
                controllableExecutionContext.runSingle()
                runned.get() shouldBe false
            }

            it("will run on local ec") {
                localEC.runSingle()
                runned.get() shouldBe true
            }
        }
    }
})

/**
 * Use it to test async code (async is called inThread).
 */
class ControllableExecutionContext : ExecutionContext, Executor {
    override fun <T> execute(f: () -> T): Future<T> = executef(f)

    override fun execute(command: Runnable): Unit = executef({ command.run() }).let { Unit }

    private val computations = AtomicReference(Computations())

    private fun <T> executef(f: () -> T): Future<T> =
        Promise.make<T>(InPlaceExecutor).let { promise ->
            val computation: Runnable = Runnable {
                try {
                    val result = f()
                    promise.success(result)
                } catch (e: Exception) {
                    promise.failure(e)
                }
            }
            computations.updateAndGet { it.addOne(computation) }
            promise.future()
        }

    internal fun runSingle() = computations.updateAndGet { list ->
        list.removeOne()
    }.lastOne?.run()

    internal fun assertEmpty() = assert(this.computations.get().computations.isEmpty)
}

internal data class Computations(val computations: List<Runnable> = List.empty(), val lastOne: Runnable? = null) {
    fun addOne(f: Runnable) = copy(
        computations = computations.append(f),
        lastOne = null
    )

    fun removeOne() = computations.headOption().map { runnable ->
        copy(computations = this.computations.pop(), lastOne = runnable)
    }.getOrElse(Computations())
}
