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
        val connection = DBTestConnection.initializeDb()
        val jdbcProvider = JDBCProvider(connection)
        val userRealm = DBUserRealm(jdbcProvider)
        context("db with a single user") {
            insertUser(testUser, testSalt, "testPass", connection)
            connection.commit()
            it("test login works") {
                val logged = userRealm.loginUser("test1", "testPass".toCharArray())
                logged.isDefined shouldBe true
            }
        }
    }
}) {

    init {
    }

    companion object {
        private val hasher = PBKDF2Hasher()

        private val testUser = User(
            UUID.fromString("b5fb0fe3-82de-4c1a-9a0c-3126bbd36733"),
            "test1",
            UserRole.roles("test", "user", "admin")
        )

        private val testSalt = UUID.fromString("699add98-2aa2-49ad-8d09-d35f2a36f36b").toBytes()

        private fun insertUser(user: User, salt: Salt, initialPassword: String, connection: Connection) =
            user.run {
                connection.prepareStatement(
                    "insert into users (id, salt, password, login)" +
                            "values (?,?,?,?)"
                ).use { stmt ->
                    stmt.setBytes(1, id.toBytes())
                    stmt.setBytes(2, salt)
                    stmt.setBytes(
                        3, hasher.hashPassword(
                            initialPassword.toCharArray(), salt
                        )
                    )
                    stmt.setString(4, login)
                    stmt.execute()
                }.also {
                    insertRoles(user, connection)
                }
            }

        private fun insertRoles(user: User, connection: Connection) =
            user.run {
                connection.prepareStatement(
                    "insert into user_roles" +
                            " (user_id, role_name)" +
                            "values (?, ?)"
                ).use { stmt ->
                    roles.forEach { userRole ->
                        stmt.setBytes(1, id.toBytes())
                        stmt.setString(2, userRole.roleName)
                        stmt.execute()
                        stmt.clearParameters()
                    }
                }
            }
    }
}