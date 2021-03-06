package dev.neeffect.nee.effects.security.oauth

import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.test.getAny
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.security.jwt.JwtConfig
import dev.neeffect.nee.security.jwt.JwtConfigurationModule
import dev.neeffect.nee.security.jwt.UserCoder
import dev.neeffect.nee.security.oauth.GoogleOpenId
import dev.neeffect.nee.security.oauth.OauthConfig
import dev.neeffect.nee.security.oauth.OauthProviderName
import dev.neeffect.nee.security.oauth.ProviderConfig
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
import io.vavr.kotlin.hashMap
import io.vavr.kotlin.option
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
            val tokens = googleOpenId.verifyOauthToken("acode", "http://localhost:8080", "anyState")
            it("calls google for tokens") {
                tokens.perform(Unit).get().tokens.idToken shouldBe otherGoogleIdToken.option()
            }
            it("gets subject ") {
                tokens.perform(Unit).get().subject shouldBe "108874454676244700380"
            }
            it("gets email ") {
                tokens.perform(Unit).get().email shouldBe some("jratajski@gmail.com")
            }
            it("gets name") {
                tokens.perform(Unit).get().displayName shouldBe some("Jarek Ratajski")
            }
        }


        it("return no jwt in case of a bad token") {
            val tokens = googleOpenId.verifyOauthToken("bad code", "http://localhost:8080", "anyState")
            tokens.perform(Unit).getAny().isLeft shouldBe true
        }
    }
}) {
    companion object {
        val googleKeysFile = GoogleOpenIdTest::class.java.getResource("/google/keys.json").toExternalForm()
        val testOauthConfig = OauthConfig(
            providers = hashMap(
                OauthProviderName.Google.providerName
                        to ProviderConfig("testId", "testSecret", googleKeysFile.option())
            )
        )
        val jwtConfig = JwtConfig(issuer = "test", signerSecret = "marny")
        val keyPath = GoogleOpenIdTest::class.java.getResourceAsStream("/keys/testServerKey.bin")
        val serveKeyPair = ServerVerifier.loadKeyPair(keyPath).get()

        val preservedState =
            "NZ1BuveK/g3hu+euKMBFDA==@BTyAoSqsaxQUq11+TWc+cRxvrkK3qcFnNivhjzu5luuoycBhyWoGaz0Z1e7bG4PO1x+onlyNAvDhKzzXpmVm2onC0tHVzRy/0pNBDXCgaeAx/mXmoIxtcMBRPObzjF1ENnu/zZ+jocni8comvOkYf1iqJAhiZNwKaixsirLaF00="

        val expectedUrl = """
        https://accounts.google.com/o/oauth2/v2/auth?
        response_type=code&
        client_id=testId&
        scope=openid%20profile%20email%20https://www.googleapis.com/auth/user.organization.read&
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


        //TODO extract and move to jwtDecodeTest along with MultiVerifier
        const val sampleGoogleToken =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImQ5NDZiMTM3NzM3Yjk3MzczOGU1Mjg2YzIwOGI2NmU3YTM5ZWU3YzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMDg4NzQ0NTQ2NzYyNDQ3MDAzODAiLCJlbWFpbCI6ImpyYXRhanNraUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6InFIR21SMUE3OUhqdEl5cW5MTl9ya2ciLCJuYW1lIjoiSmFyZWsgUmF0YWpza2kiLCJwaWN0dXJlIjoiaHR0cHM6Ly9saDQuZ29vZ2xldXNlcmNvbnRlbnQuY29tLy1lWHB2TlJLdVJyZy9BQUFBQUFBQUFBSS9BQUFBQUFBQUFBQS9BTVp1dWNtbTRzS0RCenJhakpXN0NTSVkxeUF4VzZsUGp3L3M5Ni1jL3Bob3RvLmpwZyIsImdpdmVuX25hbWUiOiJKYXJlayIsImZhbWlseV9uYW1lIjoiUmF0YWpza2kiLCJsb2NhbGUiOiJwbCIsImlhdCI6MTYwNTUyOTYyMiwiZXhwIjoxNjA1NTMzMjIyfQ.TfFiTZ4xLYcBllGqHZPAyeUn5Vo5t-hHmyja_upx-6HuXIY4RKxA_IYHX28MsCKD0hX9hX-LiZqIuZus-NKimguHmxbHxweUassraPidI-UmqTkrccFWYXE1wqLvpm_He9fwTf6imFmXnAPDT61bhTm2HQAgwZ_HOVsd8uk1j4uuIM-DHU7ndOtX88KXoXDfILKSAzOUcVwWgUgCmjuGSpd6RQ4JH7remBNcQCs0qQ7WZPKNsY1xKHj7y4LMjPpKFb3vGo1omxTeHCMmmgzS3sf7SAomqbRGUwGWi92HWv560FfXDjFf59zzmgWoNsauRXXjlMNK9QPrj7gUriq2mQ"
        const val otherGoogleIdToken =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImQ0Y2JhMjVlNTYzNjYwYTkwMDlkODIwYTFjMDIwMjIwNzA1NzRlODIiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMDg4NzQ0NTQ2NzYyNDQ3MDAzODAiLCJlbWFpbCI6ImpyYXRhanNraUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6IklRRHdGeHdXU1VFSEJJQkRIcm13Y3ciLCJub25jZSI6IjAuNjk2NDIyMSIsIm5hbWUiOiJKYXJlayBSYXRhanNraSIsInBpY3R1cmUiOiJodHRwczovL2xoNC5nb29nbGV1c2VyY29udGVudC5jb20vLWVYcHZOUkt1UnJnL0FBQUFBQUFBQUFJL0FBQUFBQUFBQUFBL0FNWnV1Y21tNHNLREJ6cmFqSlc3Q1NJWTF5QXhXNmxQancvczk2LWMvcGhvdG8uanBnIiwiZ2l2ZW5fbmFtZSI6IkphcmVrIiwiZmFtaWx5X25hbWUiOiJSYXRhanNraSIsImxvY2FsZSI6InBsIiwiaWF0IjoxNjA3MzM2OTIyLCJleHAiOjE2MDczNDA1MjJ9.DYmOVmgIf2EPmCdXUampKnOI6uEJlnopWkOCcsVJwXYAHPEU1wcD6bVOPGonM98N0pYWXU0k6NADIpvcoQkgRAC8atuOS9iqNduAq43t66fICVC503u6UlnCIwQDiMaGql3M7D3gDLNKVFQcJsyvQ-fax-mOu5bBQyAXEqbbl2SPA0T01_l2DI6j0ahit0cbC8AlJYAsEedncRvutpWNGON6MyGqV-gP4otW1sR-uTsqSM8miHRgr0BatJxzpYI5CVFwBxViXCYfbI_38B_wUmTCSBCGGslTDbZk2C0hqHtEIucTd--Yif1NSUfbVzzAlrv3Adc6-P5-a9Om4_kwsw"
        val simulatedGoogleTokenResponse =
            """
                {
                    "access_token" : "at",
                    "id_token": "$otherGoogleIdToken",
                    "refresh_token" : "some refresh token"
                }
            """.trimIndent()

        val haste = Haste.TimeSource.withFixedClock(
            Clock.fixed(Instant.parse("2020-10-24T22:22:03.00Z"), ZoneId.of("Europe/Berlin"))
        )

        val createTestModule: () -> SimpleOauthConfigModule = {
            object : SimpleOauthConfigModule(testOauthConfig, jwtConfig) {
                private val self = this
                override val randomGenerator: Random =
                    Random(42L)
                override val baseTimeProvider: TimeProvider = HasteTimeProvider(haste)

                override val keyPair: KeyPair
                    get() = serveKeyPair
                override val httpClient: HttpClient = testHttpClient
                override val jwtConfigModule by lazy {
                    object : JwtConfigurationModule<User, UserRole>(this.jwtConfig, baseTimeProvider) {
                        override val userCoder: UserCoder<User, UserRole> = self.userCoder
                    }
                }
            }
        }
    }
}
