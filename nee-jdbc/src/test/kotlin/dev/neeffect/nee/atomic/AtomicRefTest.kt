package dev.neeffect.nee.atomic

import dev.neeffect.nee.effects.test.get
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class AtomicRefTest : DescribeSpec({
    describe("simple atomic operations") {
        it("should get initial value") {
            val initial = AtomicRef(10)
            val result = initial.get().perform(Unit).get()
            result shouldBe 10
        }
        it("should get a set value") {
            val initial = AtomicRef(10)
            val result = initial.set(7).flatMap {
                initial.get()
            }.perform(Unit).get()
            result shouldBe 7
        }

        it("should getAndSet value") {
            val initial = AtomicRef(10)
            val result = initial.getAndSet(8).flatMap { x ->
                initial.get().map { y ->
                    Pair(x, y)
                }
            }.perform(Unit).get()
            result shouldBe Pair(10, 8)
        }
        it("should update value") {
            val initial = AtomicRef(10)
            val result = initial.update { x -> x + 1 }
                .flatMap { initial.get() }
                .perform(Unit).get()
            result shouldBe 11
        }
        it("should get and update value") {
            val initial = AtomicRef(10)
            val result = initial.getAndUpdate { x -> x + 1 }
                .flatMap { before ->
                    initial.get().map { after->
                        Pair(before, after)
                    }
                }.perform(Unit).get()
            result shouldBe Pair(10,11)
        }
        it("should  update and get value") {
            val initial = AtomicRef(10)
            val result = initial.updateAndGet { x -> x + 1 }
                .flatMap { before ->
                    initial.get().map { after->
                        Pair(before, after)
                    }
                }.perform(Unit).get()
            result shouldBe Pair(11,11)
        }
        it("should  modify and get  value") {
            val initial = AtomicRef(10)
            val result = initial.modifyGet { x -> Pair(x + 1, x+2) }
                .flatMap { before ->
                    initial.get().map { after->
                        Pair(before, after)
                    }
                }.perform(Unit).get()
            result shouldBe Pair(Pair(11,12),11)
        }
        it("should  modify  value") {
            val initial = AtomicRef(10)
            val result = initial.modify { x -> Pair(x + 1, x+2) }
                .flatMap { before ->
                    initial.get().map { after->
                        Pair(before, after)
                    }
                }.perform(Unit).get()
            result shouldBe Pair(12,11)
        }
        it("try update should work") {
            val initial = AtomicRef(10)
            val result = initial.tryUpdate { x -> x +1 }
                .flatMap { before ->
                    initial.get().map { after->
                        Pair(before, after)
                    }
                }.perform(Unit).get()
            result shouldBe Pair(true,11)
        }
    }
    describe("multithreaded atomic operations") {
        val random = Random(7)
        val badIncrementor = { x: Int ->
                if (random.nextInt(4) == 0) {
                    Thread.sleep(1)
                }
                Pair(x+1, 0)
        }
        val executor = Executors.newFixedThreadPool(32)
        it ("should update counter to 1000" ) {
            val initial = AtomicRef(0)
            (0 until 1000).forEach {
                executor.submit{initial.modifyGet ( badIncrementor ).perform(Unit).get()}
            }
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
            initial.get().perform(Unit).get() shouldBe 1000
        }
    }
})
