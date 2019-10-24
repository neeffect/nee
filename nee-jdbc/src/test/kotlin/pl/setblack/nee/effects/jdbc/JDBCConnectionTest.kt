package pl.setblack.nee.effects.jdbc

import io.kotlintest.specs.DescribeSpec
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe

class JDBCConnectionTest : DescribeSpec( {
    describe("jdbc connection") {
        val cfg = JDBCConfig("org.h2.Driver", "jdbc:h2:mem:test_mem","sa")
        context("connection") {
            val conn = JDBCConnection(cfg)
            it("is created") {
                conn shouldNotBe null
            }
            it("gives access to physical jdbc") {
                conn.getResource() shouldNotBe null
            }
        }
    }
})