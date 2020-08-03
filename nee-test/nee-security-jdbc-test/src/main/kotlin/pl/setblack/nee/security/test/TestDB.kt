package pl.setblack.nee.security.test

import io.vavr.collection.List
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.security.*
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

/**s
 *  Small utility for setting sql db for tests.
 */
class TestDB(val jdbcConfig: JDBCConfig = h2InMemDatabase) {

    fun initializeDb() =
        createDbConnection().let { dbConnection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(dbConnection))
            val liquibaseChangeLog = Liquibase("db/db.xml", ClassLoaderResourceAccessor(), database)
            liquibaseChangeLog.update(liquibase.Contexts(), liquibase.LabelExpression())
            TestDBConnection(dbConnection, jdbcConfig)
        }

    fun connection() =
        TestDBConnection(createDbConnection(), jdbcConfig)

    private fun createDbConnection() = DriverManager.getConnection(
        jdbcConfig.url,
        jdbcConfig.user,
        jdbcConfig.password
    )
}

class TestDBConnection(val connection: Connection, val jdbcConfig: JDBCConfig) : AutoCloseable {

    private val hasher = PBKDF2Hasher()
    private val randomGeneratorForUUID = Random(42)
    private val testSalt = UUID.fromString("699add98-2aa2-49ad-8d09-d35f2a36f36b").toBytes()


    fun addUser(login: String, password: String, roles: List<String>) =
        inTransaction(connection) { cn ->
            val uuid = UUID(randomGeneratorForUUID.nextLong(), randomGeneratorForUUID.nextLong())
            val newUser = User(
                uuid,
                login,
                roles.map { UserRole(it) }
            )
            insertUser(newUser, testSalt, password, cn)
            newUser
        }

    private fun insertUser(user: User, salt: Salt, initialPassword: String, cn: Connection) =
        user.run {
            cn.prepareStatement(
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
                insertRoles(user, cn)
            }
        }

    private fun insertRoles(user: User, cn: Connection) =
        user.run {
            cn.prepareStatement(
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


    override fun close() {
        this.connection.close()
    }

}

val h2InMemDatabase = JDBCConfig(
    driverClassName = "org.h2.Driver",
    url = "jdbc:h2:mem:test_mem;DB_CLOSE_DELAY=-1",
    user = "sa",
    password = ""
)


fun <R> inTransaction(connection: Connection, f: (Connection) -> R) {
    connection.autoCommit = false
    try {
        f(connection)
        connection.commit()
    } catch (e: Exception) {
        connection.rollback()
    } finally {

    }
}