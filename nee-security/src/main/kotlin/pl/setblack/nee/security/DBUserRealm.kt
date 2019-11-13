package pl.setblack.nee.security

import io.vavr.collection.List
import io.vavr.control.Option
import pl.setblack.nee.effects.jdbc.JDBCProvider
import java.sql.Connection
import java.util.*


data class User(
    private val id: UUID,
    private val login: String,
    internal val roles: List<UserRole>
)


data class UserRole(val roleName: String)

class DBUserRealm(private val dbProvider: JDBCProvider) : UserRealm<User, UserRole> {


    override fun loginUser(userLogin: String, password: CharArray): Option<User> =
        dbProvider.getConnection().getResource().let { jdbcConnection: Connection ->
            jdbcConnection.prepareStatement(
                "SELECT  USERID, SALT, PASSWORD  FROM FROM USERS WHERE LOGIN = ?"
            ).use { statement ->
                statement.setString(1, userLogin)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        val user = UUID.nameUUIDFromBytes(resultSet.getBytes(1))
                        val encodedSalt = resultSet.getString(2)
                        val encodedHashedPass = resultSet.getString(3)
                        val salt = Base64.getDecoder().decode(encodedSalt)
                        val passwordHash = Base64.getDecoder().decode(encodedHashedPass)

                        val inputHash = passwordHasher.hashPassword(password, salt)
                        if (passwordHash.contentEquals(inputHash)) {
                            Option.some(User(user, userLogin))
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

    private fun loadRoles(jdbcConnection: Connection, userId: UUID) :  List<UserRole> =
            jdbcConnection.prepareStatement()

    companion object {
        val passwordHasher = PBKDF2Hasher()
    }
}