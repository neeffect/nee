package pl.setblack.nee.effects.tx

import io.kotlintest.specs.BehaviorSpec
import io.kotlintest.shouldBe
import pl.setblack.nee.Nee
import pl.setblack.nee.effects.env.FlexibleEnv
import pl.setblack.nee.effects.get

internal class FlexTxEffectTest : BehaviorSpec({
    Given("FlexTxEffects") {
        val eff = FlexTxEffect<DBLike>()
        val simpleAction = Nee.constP(eff, function1.flex())
        When("run on db") {
            val db = DBLike()
            db.appendAnswer("6")
            val provider = DBLikeProvider(db)
            val env = FlexibleEnv.empty().withTxProvider(provider)
            val result = simpleAction.perform(env)
            Then("correct res") {
                println(result)
                result(Unit).get() shouldBe 6
                println(db.getLog())
            }
        }
    }
}) {
    companion object {
        val function1 = { env:FlexibleEnv ->
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