package dev.neeffect.nee.effects.async

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

internal class AsyncStackTest : DescribeSpec({
    describe("async stack") {
        val stack = CleanAsyncStack<MyEnv>()
        val env = MyEnv(1)
        it("executes action on cleanup") {
            val dirty = stack.onClose { env ->
                env.copy(test = env.test + 7)
            }
            val cleaned = dirty.cleanUp(EnvWithError(env))
            cleaned.second.r.test shouldBe 8
        }
        it("executes 2 actions on cleanup") {
            val dirty = stack.onClose { env ->
                env.copy(test = env.test + 7)
            }.onClose { env ->
                env.copy(test = env.test + 11)
            }
            val cleaned = dirty.cleanUp(EnvWithError(env))

            cleaned.second.r.test shouldBe 19
        }
        it ("stores error action") {
            val f: ()->String =  {throw IllegalStateException()}
            val res = executeAsyncCleaning(7, f, {env ->
                env
            })
            res.isLeft shouldBe true
        }
    }

}) {

    data class MyEnv(val test: Long)

}
