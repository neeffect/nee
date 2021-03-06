package dev.neeffect.nee.security

import dev.neeffect.nee.effects.jdbc.JDBCProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vavr.collection.List

class DBUserRealmTest : DescribeSpec({
    describe("dbuser realm") {
        dev.neeffect.nee.security.test.TestDB().initializeDb().use { testDb ->
            val jdbcProvider = JDBCProvider(testDb.connection)
            val userRealm = DBUserRealm(jdbcProvider)
            describe("db with a single user") {
                testDb.addUser("test1", "testPass", List.of("test", "user", "admin"))
                it("test login succeeds") {
                    val logged = userRealm.loginUser("test1", "testPass".toCharArray())
                    logged.isDefined shouldBe true
                }
                it("login with bad password fails") {
                    val logged = userRealm.loginUser("test1", "testPas1s".toCharArray())
                    logged.isDefined shouldBe false
                }

            }
        }
    }
})
