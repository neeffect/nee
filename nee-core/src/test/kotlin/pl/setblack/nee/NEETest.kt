package pl.setblack.nee

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import pl.setblack.nee.effects.get
import pl.setblack.nee.effects.tx.TestEffect
import pl.setblack.nee.effects.tx.TestResource

internal class NEETest : BehaviorSpec({
    Given("test effect and resource") {
        val effectLog = mutableListOf<String>()
        val res = TestResource(1)
        val effect= TestEffect("neetest", effectLog)
        val m1 = Nee.pure(effect) { _ ->
            {_:Unit ->
                1
            }
        }
        val m2 = {_:Int ->
            Nee.pure(effect) {r->
                {_:Unit ->
                    r.version
                }
            }
        }
        When ("flatMapped") {
            val resutl = m1.flatMap(m2)
                .perform(res)(Unit).get()
           Then("have correct env version"){
               resutl shouldBe 21
           }
        }
    }
})