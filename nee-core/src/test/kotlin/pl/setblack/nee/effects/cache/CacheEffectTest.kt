package pl.setblack.nee.effects.cache


import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.BehaviorSpec
import io.vavr.collection.Stream
import pl.setblack.nee.Nee
import pl.setblack.nee.effects.get
import java.util.concurrent.atomic.AtomicInteger

internal class CacheEffectTest : BehaviorSpec({
    Given("Cached function") {
        val cacheProvider = NaiveCacheProvider()
        val cache = CacheEffect<Env, Nothing>(cacheProvider)
        When("Called twice using same param and different env") {
            val businessFunction =
                Nee.pure(
                    cache, ::returnEnvIgnoringParam)

            val x1 = businessFunction.perform(env = Env.SomeValue)(1)
            val x2 = businessFunction.perform(env = Env.OtherValue)(1)
            Then("second call should ignore different env") {
                x2.get() shouldBe x1.get()
            }
            Then("second call should return first stored value") {
                x2.get() shouldBe Env.SomeValue
            }
        }
        When("Called twice using different params and env") {
            val businessFunction =
                Nee.pure(
                    cache, ::returnEnvIgnoringParam)

            val x2 = businessFunction.perform(env = Env.SomeValue)(1).flatMap { _ ->
                businessFunction.perform(env = Env.OtherValue)(2)
            }
            Then("second call should return other env value") {
                x2.get() shouldBe Env.OtherValue
            }
        }
    }
})

fun returnEnvIgnoringParam(env:Env) = { _:Int -> env}

sealed class Env {
    object SomeValue : Env()
    object OtherValue: Env()
}