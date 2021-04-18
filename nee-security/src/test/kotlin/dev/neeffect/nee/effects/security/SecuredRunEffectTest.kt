package dev.neeffect.nee.effects.security

import dev.neeffect.nee.Nee
import dev.neeffect.nee.effects.flatMap
import dev.neeffect.nee.effects.toFuture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.vavr.collection.List

internal class SecuredRunEffectTest : BehaviorSpec({

    Given("secure provider") {

        val secEffect = SecuredRunEffect<String, String, SimpleSecurityProvider<String, String>>("test")
        val f = Nee.with(secEffect, businessFunction)
        When("function called with test user ") {
            val testSecurityProvider = SimpleSecurityProvider<String, String>("test", List.of("test"))
            val result = f.perform(testSecurityProvider)
                .flatMap { it }
            Then("called with correct user") {
                result.toFuture().get().get() shouldBe "called by: test"
            }
        }
        When("function called without roles test user ") {
            val testSecurityProvider = SimpleSecurityProvider<String, String>("test", List.empty())
            val result = f.perform(testSecurityProvider)
                .flatMap { it }
            Then("function is not called") {
                result.toFuture().get().isLeft shouldBe true
            }
        }
    }
}) {
    companion object {
        val businessFunction = { securityProvider: SecurityProvider<String, String> ->
            securityProvider.getSecurityContext().flatMap {
                it.getCurrentUser().map {
                    "called by: $it"
                }
            }
        }
    }
}
