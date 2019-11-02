package pl.setblack.nee.effects.tx

import io.kotlintest.shouldBe
import io.kotlintest.specs.BehaviorSpec
import pl.setblack.nee.Nee
import pl.setblack.nee.effects.get

class DBEffectTest : BehaviorSpec({
    Given("TxEffects") {
        val eff = TxEffect<DBLike, DBLikeProvider>()
        val simpleAction = Nee.pure(eff, function1)
        When("runned on db") {
            val db = DBLike()
            db.appendAnswer("6")
            val provider = DBLikeProvider(db)
            val result = simpleAction.perform(provider)
            Then("correct res") {
                println(result)
                result(Unit).get() shouldBe 6
                println(db.getLog())
            }
        }
        And("nested second action") {
            val nestedAction = { orig: Int ->
                Nee.pure(eff, function2(orig))
            }
            val monad = simpleAction.flatMap(nestedAction)
            When("db connected") {
                val db = DBLike()
                db.appendAnswer("6")
                db.appendAnswer("70")
                val provider = DBLikeProvider(db)
                val result = monad.perform(provider)
                Then("correct res") {
                    println(result)
                    println(db.getLog())
                    result(Unit).get() shouldBe (1076)
                }
            }
        }
        And("nested second action in internal tx") {
            val effReqNew = TxEffect<DBLike, DBLikeProvider>(true)
            val nestedAction = { orig: Int ->
                Nee.pure(effReqNew, function2(orig))
            }
            val monad = simpleAction.flatMap(nestedAction)
            When("db connected") {
                val db = DBLike()
                db.appendAnswer("24")
                db.appendAnswer("700")
                val provider = DBLikeProvider(db)
                val result = monad.perform(provider)
                Then("correct res") {
                    println(result)
                    println(db.getLog())
                    result(Unit).get()  shouldBe(1724)
                }
            }
        }
    }
}) {
    companion object {
        val function1 = { db: DBLikeProvider ->
            { _: Unit ->
                val resource = db.getConnection().getResource()
                val result = resource.query("SELECT * FROM all1")
                result.map {
                    Integer.parseInt(it)
                }.get()
            }
        }
        val function2 = { orig : Int ->
            { db: DBLikeProvider ->
                { _: Unit ->
                    val resource = db.getConnection().getResource()
                    val result = resource.query("SELECT * FROM all2 LIMIT ${orig})")
                    result.map {
                        Integer.parseInt(it) + 1000 + orig
                    }.get()
                }
            }
        }
    }
}