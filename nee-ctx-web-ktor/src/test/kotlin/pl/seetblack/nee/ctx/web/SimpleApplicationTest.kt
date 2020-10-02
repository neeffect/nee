package pl.seetblack.nee.ctx.web

import io.kotest.core.spec.style.BehaviorSpec
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.*
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.vavr.collection.List
import pl.setblack.nee.Nee
import pl.setblack.nee.ctx.web.BasicAuth
import pl.setblack.nee.ctx.web.JDBCBasedWebContext
import pl.setblack.nee.ctx.web.WebContext
import pl.setblack.nee.ctx.web.WebContextProvider
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.security.UserRole
import pl.setblack.nee.security.test.TestDB
import kotlin.test.assertEquals

fun Application.main(wctxProvider: JDBCBasedWebContext) {

    routing {
        get("/") {
            val function = Nee.constP(wctxProvider.effects().jdbc) { webCtx ->
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
            wctxProvider.create(call).serveText(function, Unit)
        }
        get("/secured") {
            val function = Nee.constP(wctxProvider.effects().secured(List.of(UserRole("badmin")))) { _ ->
                "Secret message"
            }.anyError()
            wctxProvider.create(call).serveText(function, Unit)
        }
    }
}

class SimpleApplicationTest : BehaviorSpec({
    Given("Test ktor app") {
        val engine = TestApplicationEngine(createTestEnvironment())

        TestDB().initializeDb().use { testDb ->

            testDb.addUser("test", "test", List.of("badmin"))
            val ctxProvider = object : JDBCBasedWebContext() {
                override val jdbcProvider: JDBCProvider by lazy {
                    JDBCProvider(testDb.connection)
                }
            }
            engine.start(wait = false)
            engine.application.main(ctxProvider)

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
