package dev.neeffect.nee.ctx.web

import dev.neeffect.nee.Nee
import dev.neeffect.nee.effects.jdbc.JDBCProvider
import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.security.test.TestDB
import io.kotest.core.spec.style.BehaviorSpec
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.vavr.collection.List
import java.sql.Connection
import kotlin.test.assertEquals

fun Application.main(wctxProvider: JDBCBasedWebContextProvider) {

    routing {
        get("/") {
            val function: Nee<WebContext<Connection, JDBCProvider>, Any, String> =
                Nee.with(wctxProvider.fx().tx) { webCtx ->
                    webCtx.getConnection().getResource()
                        .prepareStatement("select 41 from dual").use { preparedStatement ->
                            preparedStatement.executeQuery().use { resultSet ->
                                if (resultSet.next()) {
                                    val result = resultSet.getString(1)
                                    "Hello! Result is $result"
                                } else {
                                    "Bad result"
                                }
                            }
                        }
                }.anyError()
            wctxProvider.create(call).serveText(function)
        }
        get("/secured") {
            val function = Nee.with(wctxProvider.fx().secured(List.of(UserRole("badmin")))) { _ ->
                "Secret message"
            }.anyError()
            wctxProvider.create(call).serveText(function)
        }
    }
}

class SimpleApplicationTest : BehaviorSpec({
    Given("Test ktor app") {
        val engine = TestApplicationEngine(createTestEnvironment())

        TestDB().initializeDb().use { testDb ->

            testDb.addUser("test", "test", List.of("badmin"))
            val ctxProvider = object : JDBCBasedWebContextProvider() {
                override val jdbcProvider: JDBCProvider by lazy {
                    JDBCProvider(testDb.connection)
                }
            }
            engine.start(wait = false)
            engine.application.main(ctxProvider)

            When("requested") {
                Then("db connection works") {
                    assertEquals("Hello! Result is 41", engine.handleRequest(HttpMethod.Get, "/").response.content)
                }

            }
            When("request with authentication") {
                Then("db connection works") {
                    engine.handleRequest(HttpMethod.Get, "/secured") {
                        addHeader(BasicAuth.authorizationHeader, "Basic dGVzdDp0ZXN0")
                    }.let { call ->
                        assertEquals("Secret message", call.response.content)
                    }
                }

            }
            When("request with invalid authentication") {
                Then("db connection works") {
                    engine.handleRequest(HttpMethod.Get, "/secured") {
                        addHeader(BasicAuth.authorizationHeader, "Basic blablador")
                    }.let { call ->
                        assertEquals(HttpStatusCode.Unauthorized, call.response.status())
                    }
                }

            }
        }
    }
})
