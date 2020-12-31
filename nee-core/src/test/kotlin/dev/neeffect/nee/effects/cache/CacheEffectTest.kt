package dev.neeffect.nee.effects.cache


import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.BehaviorSpec
import dev.neeffect.nee.Nee
import dev.neeffect.nee.effects.test.get

internal class CacheEffectTest : BehaviorSpec({
    Given("cache effect and naive implementation") {
        val cacheProvider = NaiveCacheProvider()
        val cache = CacheEffect<Env, Nothing>(cacheProvider)
        When("function called twice using same param and different env") {
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
        When("function called twice using different params and env") {
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
