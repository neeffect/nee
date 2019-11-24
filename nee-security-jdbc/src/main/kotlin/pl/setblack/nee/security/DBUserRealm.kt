package pl.setblack.nee.security

import io.vavr.collection.List
import io.vavr.control.Option
import io.vavr.kotlin.toVavrList
import pl.setblack.nee.effects.jdbc.JDBCProvider
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

data class User(
    val id: UUID,
    val login: String,
    val roles: List<UserRole>
)

data class UserRole(val roleName: String) {
    companion object {
        fun roles(vararg names: String): List<UserRole> = names.toVavrList()
            .map {UserRole(it)}
    }
}

class DBUserRealm(private val dbProvider: JDBCProvider) :
    UserRealm<User, UserRole> {

    override fun loginUser(userLogin: String, password: CharArray): Option<User> =
        dbProvider.getConnection().getResource().let { jdbcConnection: Connection ->
            jdbcConnection.prepareStatement(
                "select  id, salt, password  from users where login = ?"
            ).use { statement ->
                statement.setString(1, userLogin)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        val user = resultSet.getBytes(1).toUUID()
                        val salt = resultSet.getBytes(2)
                        val passwordHash = resultSet.getBytes(3)
                        val inputHash = passwordHasher.hashPassword(password, salt)
                        if (passwordHash.contentEquals(inputHash)) {
                            Option.some(User(user, userLogin, loadRoles(jdbcConnection, user)))
                        } else {
                            Option.none()
                        }
                    } else {
                        Option.none()
                    }
                }
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
    }
}

fun ByteArray.toUUID() =
    ByteBuffer.wrap(this).let {
        UUID(it.long, it.long)
    }

fun UUID.toBytes(): ByteArray =
    ByteBuffer.wrap(ByteArray(16)).let {
        it.putLong(this.mostSignificantBits)
        it.putLong(this.leastSignificantBits)
        return it.array()
    }





