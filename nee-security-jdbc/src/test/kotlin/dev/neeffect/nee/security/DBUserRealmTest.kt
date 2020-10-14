package dev.neeffect.nee.security

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.DescribeSpec
import java.sql.Connection
import java.util.*
import io.vavr.collection.List
import dev.neeffect.nee.effects.jdbc.JDBCProvider

class DBUserRealmTest : DescribeSpec({
    describe("dbuser realm") {
        dev.neeffect.nee.security.test.TestDB().initializeDb().use {testDb ->
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
}) {

}
