package pl.setblack.nee.effects.security

import io.kotlintest.shouldBe
import io.kotlintest.specs.BehaviorSpec
import io.vavr.collection.List
import pl.setblack.nee.Nee

internal class SecuredRunEffectTest : BehaviorSpec({

    Given("secure provider") {

        val secEffect = SecuredRunEffect<String, String, SimpleSecurityProvider<String, String>>("test")
        val f = Nee.constP(secEffect, businessFunction)
        When("function called with test user ") {
            val testSecurityProvider = SimpleSecurityProvider<String, String>("test", List.of("test"))
            val result = f.perform(testSecurityProvider)(Unit)
                .flatMap { it }
            Then("called with correct user") {
                result.toFuture().get().get() shouldBe "called by: test"
            }
        }
        When("function called without roles test user ") {
            val testSecurityProvider = SimpleSecurityProvider<String, String>("test", List.empty())
            val result = f.perform(testSecurityProvider)(Unit)
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
