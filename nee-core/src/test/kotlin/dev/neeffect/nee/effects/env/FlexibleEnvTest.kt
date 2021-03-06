package dev.neeffect.nee.effects.env

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.vavr.control.Option
import io.vavr.kotlin.some

class FlexibleEnvTest : BehaviorSpec({
    Given("Simple env") {
        val res = MyResource("test value")
        val env = FlexibleEnv.create(res)
        When("asked for env") {
            val value = env.get(ResourceId(MyResource::class))
            Then("resource is returned") {
                value shouldBe some(res)
            }
        }
        When("asked for non existing res") {
            val value = env.get(ResourceId(String::class))
            Then("no result is given") {
                value shouldBe Option.none()
            }
        }
        And("extended with another env") {
            val another = MyOtherResource("test2")
            val anotherResKey = ResourceId(MyOtherResource::class)
            val env2 = env.with(anotherResKey, another)
            When("asked for another") {
                val anotherVal = env2.get(anotherResKey)
                Then("another value is given") {
                    anotherVal shouldBe some(another)
                }
            }
            When("asked for initial val") {
                val value = env2.get(ResourceId(MyResource::class))
                Then("resource is returned") {
                    value shouldBe some(res)
                }
            }
        }
    }
}) {

    data class MyResource(val internalVal: String)
    data class MyOtherResource(val internalVal: String)

}
