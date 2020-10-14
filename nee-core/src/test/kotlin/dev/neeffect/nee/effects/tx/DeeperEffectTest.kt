package dev.neeffect.nee.effects.tx

import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.core.spec.style.BehaviorSpec
import dev.neeffect.nee.Nee
import dev.neeffect.nee.andThen
import dev.neeffect.nee.effects.get

class DeeperEffectTest  : BehaviorSpec ({
    Given("TestEffect") {
        val log = mutableListOf<String>()
        val eff = TestEffect("ef1", log)
        val nee = Nee.Companion.pure(eff, function1(log))
        When("called") {
            val res = nee.perform(TestResource(1))(Unit)
            Then ("log is ok") {
                ///TODO() write expectations on order of items in log
                println(res)
                println(log)
                res.get() should be ("OK")
            }
        }
    }
    Given("Two TestEffects") {
        val log = mutableListOf<String>()
        val eff1 = TestEffect("ef1", log)
        val eff2 = TestEffect("ef2", log)
        val eff = eff1.andThen(eff2)
        val nee = Nee.Companion.pure(eff, function1(log))
        When("called") {
            val res = nee.perform(TestResource(1))(Unit)
            Then ("log is ok") {
                println(res)
                println(log)
                res.get() should be ("OK")
            }
        }
    }

}) {
    companion object {
        fun function1(log : MutableList<String>) = { db: TestResource ->
            { _:Unit ->
                log.add("function1 called with: $db")
                 "OK"
            }
        }
    }
}
