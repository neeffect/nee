package pl.setblack.nee.security

import io.vavr.collection.List
import io.vavr.control.Option
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.security.DBUserRealm.Companion.uuidByteSize
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

/**
 * Security realm with classic User and Roles tables.
 */
class DBUserRealm(private val dbProvider: JDBCProvider) :
    UserRealm<User, UserRole> {

    override fun loginUser(userLogin: String, password: CharArray): Option<User> =
        dbProvider.getConnection().getResource().let { jdbcConnection: Connection ->
            jdbcConnection.prepareStatement(
                "select  id, salt, password  from users where login = ?"
            ).use { statement ->
                findUserInDB(statement, userLogin, password, jdbcConnection)
            }
        }

    private fun findUserInDB(
        statement: PreparedStatement,
        userLogin: String,
        password: CharArray,
        jdbcConnection: Connection
    ): Option<User> {
        statement.setString(1, userLogin)
        return statement.executeQuery().use { resultSet ->
            if (resultSet.next()) {
                checkDBRow(resultSet, password, userLogin, jdbcConnection)
            } else {
                Option.none()
            }
        }
    }

    private fun checkDBRow(
        resultSet: ResultSet,
        password: CharArray,
        userLogin: String,
        jdbcConnection: Connection
    ): Option<User> {
        val user = resultSet.getBytes(userIdColumn).toUUID()
        val salt = resultSet.getBytes(saltColumn)
        val passwordHash = resultSet.getBytes(passHashColumn)
        val inputHash = passwordHasher.hashPassword(password, salt)
        return if (passwordHash.contentEquals(inputHash)) {
            Option.some(User(user, userLogin, loadRoles(jdbcConnection, user)))
        } else {
            Option.none()
        }
    }

    override fun hasRole(user: User, role: UserRole): Boolean =
        user.roles.contains(role)

    private fun loadRoles(jdbcConnection: Connection, userId: UUID): List<UserRole> =
        jdbcConnection.prepareStatement("SELECT role_name FROM user_roles WHERE user_id = ?").use { statement ->
            statement.setBytes(1, userId.toBytes())
            statement.executeQuery().use { resultSet: ResultSet ->
                var roles = List.empty<UserRole>()
                while (resultSet.next()) {
                    val roleName = resultSet.getString(1)
                    roles = roles.prepend(UserRole(roleName))
                }
                roles
            }
        }

    companion object {
        val passwordHasher = PBKDF2Hasher()
        internal const val uuidByteSize = 16
        private const val userIdColumn = 1
        private const val saltColumn = 2
        private const val passHashColumn = 3
    }
}

fun ByteArray.toUUID() =
    ByteBuffer.wrap(this).let {
        UUID(it.long, it.long)
    }

fun UUID.toBytes(): ByteArray =
    ByteBuffer.wrap(ByteArray(uuidByteSize)).let {
        it.putLong(this.mostSignificantBits)
        it.putLong(this.leastSignificantBits)
        return it.array()
    }





