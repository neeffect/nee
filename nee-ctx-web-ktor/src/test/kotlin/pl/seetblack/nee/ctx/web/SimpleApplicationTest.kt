package pl.seetblack.nee.ctx.web

import io.kotlintest.specs.BehaviorSpec
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.vavr.collection.List
import pl.setblack.nee.Nee
import pl.setblack.nee.ctx.web.BasicAuth
import pl.setblack.nee.ctx.web.WebContext
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.security.UserRole
import pl.setblack.nee.security.test.TestDB
import kotlin.test.assertEquals

fun Application.main(jdbcConfig: JDBCConfig) {
    routing {
        get("/") {
            val function = Nee.constP(WebContext.Effects.jdbc) { webCtx ->
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
            WebContext.create(jdbcConfig, call).serveText(function, Unit)
        }
        get("/secured") {
            val function = Nee.constP(WebContext.Effects.secured(List.of(UserRole("badmin")))) { _ ->
                "Secret message"
            }.anyError()
            WebContext.create(jdbcConfig, call).serveText(function, Unit)
        }
    }
}

class SimpleApplicationTest : BehaviorSpec({
    Given("Test ktor app") {
        val engine = TestApplicationEngine(createTestEnvironment())
        val testDb = TestDB()
        testDb.addUser("test", "test",List.of("badmin"))
        engine.start(wait = false)
        engine.application.main(testDb.jdbcConfig)

        When("requested") {
            Then("db connection works") {
                engine.handleRequest(HttpMethod.Get, "/").let { call ->
                    assertEquals("Hello! Result is 41", call.response.content)
                }
            }

        }
        When("request with authentication") {
            Then("db connection works") {
                engine.handleRequest(HttpMethod.Get, "/secured") {
                    addHeader(BasicAuth.authorizationHeader, "dGVzdDp0ZXN0")
                }.let { call ->
                    assertEquals("Secret message", call.response.content)
                }
            }

        }
    }
})
