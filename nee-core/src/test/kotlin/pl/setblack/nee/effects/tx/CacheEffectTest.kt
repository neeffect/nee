package pl.setblack.nee.effects.tx


import io.kotlintest.shouldBe
import io.kotlintest.specs.BehaviorSpec
import io.vavr.collection.Stream
import pl.setblack.nee.NEE
import pl.setblack.nee.effects.cache.CacheEffect
import pl.setblack.nee.effects.cache.NaiveCacheProvider
import pl.setblack.nee.effects.get
import java.util.concurrent.atomic.AtomicInteger

internal class CacheEffectTest : BehaviorSpec({
    Given("Cache effect") {
        val cacheProvider = NaiveCacheProvider()
        val cache = CacheEffect<Unit, Nothing>(cacheProvider)
        When("Called 100 times with different params") {
            val calc = Calculator()
            val businessFunction =
                NEE.Companion.pure(
                    cache
                ) { _: Unit ->
                    calc::add
                }
            Stream.range(0, 100).forEach { businessFunction.perform(Unit)(Pair(it, 2)) }
            Then("registered 100 calls") {
                calc.counter.get() shouldBe 100
            }
        }
        When("Called 100 times with same param") {
            val calc = Calculator()
            val businessFunction =
                NEE.Companion.pure(
                    cache
                ) { _: Unit ->
                    calc::add
                }
            Stream.range(0, 100).forEach { businessFunction.perform(Unit)(Pair(1, 5)) }
            Then("single call") {
                calc.counter.get() shouldBe 1
            }
            Then("calculator still works on cached data") {
                businessFunction.perform(Unit)(Pair(37, 5)).get() shouldBe 42
            }
            Then("calculator still works on uncached data") {
                businessFunction.perform(Unit)(Pair(31, 32)).get() shouldBe 63
            }
        }
    }
})

class Calculator {
    val counter = AtomicInteger(0)
    fun add(vals: Pair<Int, Int>) = (vals.first + vals.second).also { counter.incrementAndGet() }
}