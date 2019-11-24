package pl.setblack.nee.security

import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.DescribeSpec
import java.sql.Connection
import java.util.*
import io.vavr.collection.List
import pl.setblack.nee.effects.jdbc.JDBCProvider

class DBUserRealmTest : DescribeSpec({
    describe("dbuser realm") {
        val testDb = pl.setblack.nee.security.test.TestDB()
        val jdbcProvider = JDBCProvider(testDb.initializeDb())
        val userRealm = DBUserRealm(jdbcProvider)
        context("db with a single user") {
            testDb.addUser("test1","testPass", List.of("test", "user", "admin"))
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
}) {

}