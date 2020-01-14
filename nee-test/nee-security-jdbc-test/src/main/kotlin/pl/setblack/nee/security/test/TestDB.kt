package pl.setblack.nee.security.test

import io.vavr.collection.List
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.security.PBKDF2Hasher
import pl.setblack.nee.security.Salt
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRole
import pl.setblack.nee.security.toBytes
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

class TestDB(val jdbcConfig: JDBCConfig = h2InMemDatabase) {


    private val hasher = PBKDF2Hasher()
    private val randomGeneratorForUUID = Random(42)
    private val testSalt = UUID.fromString("699add98-2aa2-49ad-8d09-d35f2a36f36b").toBytes()

    fun initializeDb() =
        createDbConnection().use { dbConnection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(dbConnection))
            val liquibaseChangeLog = Liquibase("db/db.xml", ClassLoaderResourceAccessor(), database)
            liquibaseChangeLog.update(liquibase.Contexts(), liquibase.LabelExpression())
        }.let { createDbConnection() }

    fun connection() = createDbConnection()

    fun addUser(login: String, password : String, roles:List<String>) =
        initializeDb().use {connection ->
            val uuid = UUID(randomGeneratorForUUID.nextLong(), randomGeneratorForUUID.nextLong())
            val newUser = User(
                uuid,
                login,
                roles.map { UserRole(it)}
            )
            insertUser(newUser, testSalt, password, connection)
            newUser
        }

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


    private fun createDbConnection() = DriverManager.getConnection(
        jdbcConfig.url,
        jdbcConfig.user,
        jdbcConfig.password
    )
}

val h2InMemDatabase =  JDBCConfig(
    driverClassName = "org.h2.Driver",
    url = "jdbc:h2:mem:test_mem;DB_CLOSE_DELAY=-1",
    user = "sa",
    password = ""
)