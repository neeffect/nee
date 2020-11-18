package dev.neeffect.nee.effects.security.oauth

import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.test.getAny
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.security.jwt.JwtConfig
import dev.neeffect.nee.security.jwt.JwtConfigurationModule
import dev.neeffect.nee.security.oauth.GoogleOpenId
import dev.neeffect.nee.security.oauth.OauthConfig
import dev.neeffect.nee.security.oauth.SimpleOauthConfigModule
import dev.neeffect.nee.security.state.ServerVerifier
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import io.vavr.kotlin.some
import java.security.KeyPair
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*

internal class GoogleOpenIdTest : DescribeSpec({
    describe("gooogle open id") {
        val testModule = GoogleOpenIdTest.createTestModule()
        val googleOpenId = GoogleOpenId(testModule)
        it("generates api call url") {
            val url = googleOpenId.generateApiCall("lokal-post")
            url shouldBe expectedUrl
        }
        describe("tokens") {
            val tokens = googleOpenId.verifyOauthToken("acode")
            it("calls google for tokens") {
                tokens.perform(Unit)(Unit).get().tokens.idToken shouldBe sampleGoogleToken
            }
            it("gets subject ") {
                tokens.perform(Unit)(Unit).get().subject shouldBe "108874454676244700380"
            }
            it("gets email ") {
                tokens.perform(Unit)(Unit).get().email shouldBe some("jratajski@gmail.com")
            }
            it("gets name") {
                tokens.perform(Unit)(Unit).get().displayName shouldBe some("Jarek Ratajski")
            }

        }


        it("return no jwt in case of a bad token") {
            val tokens = googleOpenId.verifyOauthToken("bad code")
            tokens.perform(Unit)(Unit).getAny().isLeft shouldBe true
        }
    }
}) {
    companion object {

        val testOauthConfig = OauthConfig("testId", "testSecret")
        val jwtConfig = JwtConfig(issuer = "test", signerSecret = "marny")
        val keyPath = GoogleOpenIdTest::class.java.getResourceAsStream("/keys/testServerKey.bin")
        val serveKeyPair = ServerVerifier.loadKeyPair(keyPath).get()

        val preservedState =
            "NZ1BuveK/g3hu+euKMBFDA==@BTyAoSqsaxQUq11+TWc+cRxvrkK3qcFnNivhjzu5luuoycBhyWoGaz0Z1e7bG4PO1x+onlyNAvDhKzzXpmVm2onC0tHVzRy/0pNBDXCgaeAx/mXmoIxtcMBRPObzjF1ENnu/zZ+jocni8comvOkYf1iqJAhiZNwKaixsirLaF00="

        val expectedUrl = """
        https://accounts.google.com/o/oauth2/v2/auth?
        response_type=code&
        client_id=testId&
        scope=openid&
        redirect_uri=lokal-post&
        state=${preservedState}&
        login_hint=jsmith@example.com&
        nonce=0.3087194
        """.trimIndent().replace("\n", "")


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

        val haste = Haste.TimeSource.withFixedClock(
            Clock.fixed(Instant.parse("2020-10-24T22:22:03.00Z"), ZoneId.of("Europe/Berlin"))
        )

        val createTestModule: () -> SimpleOauthConfigModule = {
            object : SimpleOauthConfigModule(testOauthConfig, jwtConfig) {
                override val rng: Random by lazy {
                    Random(42L)
                }
                override val keyPair: KeyPair
                    get() = serveKeyPair
                override val httpClient: HttpClient
                    get() = testHttpClient
                override val jwtConfigModule: JwtConfigurationModule by lazy {
                    object : JwtConfigurationModule(this.jwtConfig) {
                        override val timeProvider: TimeProvider
                            get() = HasteTimeProvider(haste)
                    }
                }
            }
        }
    }
}
