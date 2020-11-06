package dev.neeffect.nee.ctx.web.oauth

import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.security.jwt.JwtConfig
import dev.neeffect.nee.security.oauth.OauthConfig
import dev.neeffect.nee.security.oauth.OauthService
import dev.neeffect.nee.security.oauth.SimpleOauthConfigModule
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*

internal class OauthSupportApiTest : DescribeSpec({
    describe("oauth support") {

        val engine = TestApplicationEngine(createTestEnvironment())
        engine.start(wait = false)
        installTestApp(engine.application)
        it("generates url") {
            val content = engine.handleRequest(
                HttpMethod.Get,
                "/generateUrl/Google?redirect=http://localhost/save"
            ).response.content
            content shouldContain ("state=")

        }
        it("logs user in") {
            val oauthData  = OauthLoginData(
                code = "acode",
                state  = "fake state"
            )
            val content = engine.handleRequest(
                HttpMethod.Post,
                "/loginUser/Google"

            ) {
                this.setBody(DefaultJacksonMapper.mapper.writeValueAsString(oauthData))
                this.addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }.response.content
            content shouldContain ("encodedToken")
        }
    }
}) {
    companion object {
        val testOauthConfig = OauthConfig("testId", "testSecret")
        val jwtConfig = JwtConfig(issuer = "test", signerSecret = "marny")
        const val sampleGoogleToken =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImQ5NDZiMTM3NzM3Yjk3MzczOGU1Mjg2YzIwOGI2NmU3YTM5ZWU3YzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMDg4NzQ0NTQ2NzYyNDQ3MDAzODAiLCJlbWFpbCI6ImpyYXRhanNraUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6InFIR21SMUE3OUhqdEl5cW5MTl9ya2ciLCJuYW1lIjoiSmFyZWsgUmF0YWpza2kiLCJwaWN0dXJlIjoiaHR0cHM6Ly9saDQuZ29vZ2xldXNlcmNvbnRlbnQuY29tLy1lWHB2TlJLdVJyZy9BQUFBQUFBQUFBSS9BQUFBQUFBQUFBQS9BTVp1dWNtbTRzS0RCenJhakpXN0NTSVkxeUF4VzZsUGp3L3M5Ni1jL3Bob3RvLmpwZyIsImdpdmVuX25hbWUiOiJKYXJlayIsImZhbWlseV9uYW1lIjoiUmF0YWpza2kiLCJsb2NhbGUiOiJwbCIsImlhdCI6MTYwNTUyOTYyMiwiZXhwIjoxNjA1NTMzMjIyfQ.TfFiTZ4xLYcBllGqHZPAyeUn5Vo5t-hHmyja_upx-6HuXIY4RKxA_IYHX28MsCKD0hX9hX-LiZqIuZus-NKimguHmxbHxweUassraPidI-UmqTkrccFWYXE1wqLvpm_He9fwTf6imFmXnAPDT61bhTm2HQAgwZ_HOVsd8uk1j4uuIM-DHU7ndOtX88KXoXDfILKSAzOUcVwWgUgCmjuGSpd6RQ4JH7remBNcQCs0qQ7WZPKNsY1xKHj7y4LMjPpKFb3vGo1omxTeHCMmmgzS3sf7SAomqbRGUwGWi92HWv560FfXDjFf59zzmgWoNsauRXXjlMNK9QPrj7gUriq2mQ"

        val simulatedGoogleTokenResponse =
            """
                {
                    "access_token" : "at",
                    "id_token": "$sampleGoogleToken",
                    "refresh_token" : "some refresh token"
                }
            """.trimIndent()
        //TODO this is copied
        val testHttpClient = HttpClient(MockEngine) {
            install(JsonFeature) {
                serializer = JacksonSerializer()
            }
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        "https://oauth2.googleapis.com/token" -> {
                            val content = request.body.toByteArray().decodeToString()
                            val params = content.parseUrlEncodedParameters()
                            val code = params["code"]
                            if (code == "acode") {
                                val responseHeaders =
                                    headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
                                respond(simulatedGoogleTokenResponse, headers = responseHeaders)
                            } else {
                                respond("I hate you", HttpStatusCode.Forbidden)
                            }
                        }
                        else -> error("Unhandled ${request.url}")
                    }
                }
            }
        }
        val haste = Haste.TimeSource.withFixedClock(
            Clock.fixed(Instant.parse("2020-10-24T22:22:03.00Z"), ZoneId.of("Europe/Berlin"))
        )
        val oauthConfigModule = object : SimpleOauthConfigModule(testOauthConfig, jwtConfig) {
            override val randomGenerator: Random = Random(1)
            override val baseTimeProvider: TimeProvider = HasteTimeProvider(haste)
            override val httpClient: HttpClient = testHttpClient
        }
        val oauthService = OauthService<User, UserRole>(oauthConfigModule)
        val oauthSupportApi = OauthSupportApi(oauthService)
        fun installTestApp(app: Application) {
            app.routing {
                oauthSupportApi.oauthApi()()
            }
        }
    }
}
