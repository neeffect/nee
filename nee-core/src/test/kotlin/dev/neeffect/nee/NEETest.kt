package dev.neeffect.nee

import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.tx.TestEffect
import dev.neeffect.nee.effects.tx.TestResource
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

internal class NEETest : BehaviorSpec({
    Given("test effect and resource") {
        val effectLog = mutableListOf<String>()
        val res = TestResource(1)
        val effect = TestEffect("neetest", effectLog)
        val m1 = Nee.with(effect) { _ ->
            1
        }
        val m2 = { _: Int ->
            Nee.with(effect) { r ->
                r.version
            }
        }
        When("flatMapped") {
            val resutl = m1.flatMap(m2)
                .perform(res).get()
            Then("have correct env version") {
                resutl shouldBe 21
            }
        }
    }
})
