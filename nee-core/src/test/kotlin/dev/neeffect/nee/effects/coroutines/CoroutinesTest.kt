package dev.neeffect.nee.effects.coroutines

import dev.neeffect.nee.Nee
import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.go
import dev.neeffect.nee.runNee
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

class CoroutinesTest : DescribeSpec({
    describe("Nee in coroutines") {
        val x = Nee.pure<Int, Int, Int> {
            1.also {
                println("x")
            }
        }
        val y =
            Nee.pure<Int, Int, Int> {
                2.also {
                    println("y")
                }
            }
        it("should be processed") {
            val z: Int = runBlocking {
                with(1) {
                    val a: Int = x.go()()
                    val b = y.go()() + a
                    b
                }
            }
            z shouldBe 3
        }
    }

    describe("Nee from coroutines") {
        it("should create Nee instance") {
            val x = suspend { 1 }
            val result = runNee {
                x()
            }
            result.perform(Unit).get() shouldBe 1
        }
    }
})
