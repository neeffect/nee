package dev.neeffect.nee.effects.tx

import dev.neeffect.nee.Nee
import dev.neeffect.nee.effects.env.FlexibleEnv
import dev.neeffect.nee.effects.test.get
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

internal class FlexTxEffectTest : BehaviorSpec({
    Given("FlexTxEffects") {
        val eff = FlexTxEffect<DBLike>()
        val simpleAction = Nee.with(eff, function1.flex())
        When("run on db") {
            val db = DBLike()
            db.appendAnswer("6")
            val provider = DBLikeProvider(db)
            val env = FlexibleEnv.empty().withTxProvider(provider)
            val result = simpleAction.perform(env)
            Then("correct res") {
                result.get() shouldBe 6
            }
        }
    }
}) {
    companion object {
        val function1 = { env: FlexibleEnv ->
            val resource = FlexTxProvider.connection<DBLike>(env)
            val result = resource.query("SELECT * FROM all1")
            result.map {
                Integer.parseInt(it)
            }.get()
        }

//        val function2 = { orig: Int ->
//            { db: FlexTxProvider<DBLike> ->
//                val resource = db.getConnection().getResource()
//                val result = resource.query("SELECT * FROM all2 LIMIT ${orig})")
//                result.map {
//                    Integer.parseInt(it) + 1000 + orig
//                }.get()
//            }
//        }
    }
}
