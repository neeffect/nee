package dev.neeffect.nee.effects.cache

import dev.neeffect.nee.Nee
import dev.neeffect.nee.effects.flatMap
import dev.neeffect.nee.effects.test.get
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

internal class CacheEffectTest : BehaviorSpec({
    Given("cache effect and naive implementation") {
        val cacheProvider = NaiveCacheProvider()
        val cache = { p: Int -> CacheEffect<Env, Nothing, Int>(p, cacheProvider) }
        When("function called twice using same param and different env") {
            fun businessFunction(p: Int) =
                Nee.with(
                    cache(p), ::returnEnvIgnoringParam
                )

            val x1 = businessFunction(1).perform(env = Env.SomeValue)
            val x2 = businessFunction(1).perform(env = Env.OtherValue)
            Then("second call should ignore different env") {
                x2.get() shouldBe x1.get()
            }
            Then("second call should return first stored value") {
                x2.get() shouldBe Env.SomeValue
            }
        }
        When("function called twice using different params and env") {
            fun businessFunction(p: Int) =
                Nee.with(
                    cache(p), ::returnEnvIgnoringParam
                )

            val x2 = businessFunction(1).perform(env = Env.SomeValue).flatMap { _ ->
                businessFunction(2).perform(env = Env.OtherValue)
            }
            Then("second call should return other env value") {
                x2.get() shouldBe Env.OtherValue
            }
        }
    }
})

fun returnEnvIgnoringParam(env: Env) = env

sealed class Env {
    object SomeValue : Env()
    object OtherValue : Env()
}
