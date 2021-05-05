package dev.neeffect.nee.ctx.web.pure

import com.fasterxml.jackson.databind.ObjectMapper
import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.BaseWebContextProvider
import dev.neeffect.nee.ctx.web.DefaultErrorHandler
import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.ctx.web.ErrorHandler
import dev.neeffect.nee.ctx.web.WebContextProvider
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.tx.DummyTxProvider
import dev.neeffect.nee.effects.tx.TxProvider
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.jackson.JacksonConverter
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class PureKtorTest : DescribeSpec({
    describe("test routing") {
        it("returns get hello") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Get, "/hello")) {
                    response.content shouldBe "\"hello world\""
                }
            }
        }
        it("returns error code") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Get, "/error")) {
                    response.status() shouldBe HttpStatusCode.ExpectationFailed
                }
            }
        }
        it("returns on post ") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Post, "/hello")) {
                    response.content shouldBe "\"posted\""
                }
            }
        }

        it("returns on delete ") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Delete, "/hello")) {
                    response.content shouldBe "\"deleted\""
                }
            }
        }
        it("returns on patch ") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Patch, "/hello")) {
                    response.content shouldBe "\"patched\""
                }
            }
        }
        it("returns on head ") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Head, "/hello")) {
                    response.content shouldBe "\"headed\""
                }
            }
        }
        it("returns on option ") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Options, "/hello")) {
                    response.content shouldBe "\"optioned\""
                }
            }
        }
        it("returns nested post ") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Post, "/nested")) {
                    response.content shouldBe "\"nested\""
                }
            }
        }
        it("returns 404 error on non existing ") {
            withTestApplication(rest) {
                with(handleRequest(HttpMethod.Delete, "/nested")) {
                    response.status() shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }
}) {
    companion object {
        val constTime = HasteTimeProvider(
            Haste.TimeSource.withFixedClock(
                Clock.fixed(
                    LocalDateTime.parse("2021-04-10T10:10:10").toInstant(ZoneOffset.UTC),
                    ZoneId.of("UTC")
                )
            )
        )

        val errorHandler: ErrorHandler = { error ->
            when (error) {
                is Error -> TextContent(
                    text = "error: $error",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.ExpectationFailed
                )
                else -> DefaultErrorHandler(error)
            }
        }

        val testContextProvider: BaseWebContextProvider<Nothing, DummyTxProvider> =
            BaseWebContextProvider.createTransient(
                customErrorHandler = errorHandler
            )

        val routeBuilder = testContextProvider.routeBuilder()

        val testRouting = routeBuilder.get("/hello") {
            Nee.success { "hello world" }
        } + routeBuilder.get("/error") {
            Nee.fail("expectedError")
        } + routeBuilder.post("/hello") {
            Nee.success { "posted" }
        } + routeBuilder.delete("/hello") {
            Nee.success { "deleted" }
        } + routeBuilder.nested("/nested") {
            routeBuilder.post {
                Nee.success { "nested" }
            }
        } + routeBuilder.patch("/hello") {
            Nee.success { "patched" }
        } + routeBuilder.head("/hello") {
            Nee.success { "headed" }
        } + routeBuilder.options("/hello") {
            Nee.success { "optioned" }
        }

        val rest: Application.() -> Unit = testApplication(
            DefaultJacksonMapper.mapper,
            testContextProvider
        ) {
            it + testRouting
        }
    }
}



typealias Error = String

fun <R, G : TxProvider<R, G>> testApplication(
    mapper: ObjectMapper,
    webContextProvider: WebContextProvider<R, G>,
    aRouting: (Routing<R, G>) -> RoutingDef<R, G>
): Application.() -> Unit = {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(mapper))
    }
    val initialRouting = InitialRouting<R, G>()
    routing {
        aRouting(initialRouting).buildRoute(this, webContextProvider)
    }
}
