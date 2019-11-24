package pl.seetblack.nee.ctx.web

import io.kotlintest.specs.BehaviorSpec
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.HttpMethod
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.sessions.*
import io.ktor.util.toByteArray
import kotlinx.coroutines.runBlocking
import pl.setblack.nee.Nee
import pl.setblack.nee.ctx.web.WebContext
import pl.setblack.nee.ctx.web.WebCtxEffects
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.security.test.TestDB
import kotlin.test.assertEquals
import kotlin.test.assertFalse

fun Application.main(jdbcConfig: JDBCConfig) {
    routing {
        get("/") {
            val function = Nee.constP(WebCtxEffects.jdbc) { webCtx ->
                val connection = webCtx.getConnection().getResource()
                connection.prepareStatement("select 41 from dual" ).use {
                    preparedStatement ->
                    preparedStatement.executeQuery().use {
                        resultSet ->
                        if (resultSet.next() ) {
                            val result = resultSet.getString(1)
                            "Hello! Result is $result"
                        } else {
                            "Bad result"
                        }
                    }
                }
            }
            val result = function.perform(WebContext.create(jdbcConfig, call))(Unit)
            result.map {text ->
                runBlocking { call.respondText(text) }
            }
        }
    }
}

class SimpleApplicationTest : BehaviorSpec({
    Given("Test ktor app") {
        val engine = TestApplicationEngine(createTestEnvironment())
        val testDb = TestDB()
        engine.start(wait = false)
        engine.application.main(testDb.jdbcConfig)

        When("requested") {
            Then ("db connection works") {
                engine.handleRequest(HttpMethod.Get, "/").let { call ->
                    assertEquals("Hello! Result is 41", call.response.content)
                }
            }
        }
    }
})
