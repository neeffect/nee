package dev.neeffect.nee.ctx.web.oauth

import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.security.jwt.JwtConfig
import dev.neeffect.nee.security.oauth.OauthConfig
import dev.neeffect.nee.security.oauth.OauthProviderName
import dev.neeffect.nee.security.oauth.OauthService
import dev.neeffect.nee.security.oauth.ProviderConfig
import dev.neeffect.nee.security.oauth.SimpleOauthConfigModule
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.vavr.jackson.datatype.VavrModule
import io.vavr.kotlin.hashMap
import io.vavr.kotlin.option
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
                "/oauth/generateUrl/Google?redirect=http://localhost/save"
            ).response.content
            content shouldContain ("state=")

        }
        it("logs user in") {
            val oauthData = OauthLoginData(
                code = "acode",
                state = oauthConfigModule.serverVerifier.generateRandomSignedState(),
                redirectUri = "http://localhost:8080"
            )
            val content = engine.handleRequest(
                HttpMethod.Post,
                "/oauth/loginUser/Google"

            ) {
                this.setBody(DefaultJacksonMapper.mapper.writeValueAsString(oauthData))
                this.addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }.response.content
            content shouldContain ("encodedToken")
        }
    }
}) {
    companion object {
        val googleCertificateFile = OauthSupportApiTest::class.java.getResource("/google/keys.json")

        val testOauthConfig = OauthConfig(
            providers = hashMap(
                OauthProviderName.Google.providerName
                        to ProviderConfig("testId", "testSecret", googleCertificateFile.toExternalForm().option())
            )
        )
        val jwtConfig = JwtConfig(issuer = "test", signerSecret = "marny")

        const val otherGoogleIdToken =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6ImQ0Y2JhMjVlNTYzNjYwYTkwMDlkODIwYTFjMDIwMjIwNzA1NzRlODIiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMDg4NzQ0NTQ2NzYyNDQ3MDAzODAiLCJlbWFpbCI6ImpyYXRhanNraUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6IklRRHdGeHdXU1VFSEJJQkRIcm13Y3ciLCJub25jZSI6IjAuNjk2NDIyMSIsIm5hbWUiOiJKYXJlayBSYXRhanNraSIsInBpY3R1cmUiOiJodHRwczovL2xoNC5nb29nbGV1c2VyY29udGVudC5jb20vLWVYcHZOUkt1UnJnL0FBQUFBQUFBQUFJL0FBQUFBQUFBQUFBL0FNWnV1Y21tNHNLREJ6cmFqSlc3Q1NJWTF5QXhXNmxQancvczk2LWMvcGhvdG8uanBnIiwiZ2l2ZW5fbmFtZSI6IkphcmVrIiwiZmFtaWx5X25hbWUiOiJSYXRhanNraSIsImxvY2FsZSI6InBsIiwiaWF0IjoxNjA3MzM2OTIyLCJleHAiOjE2MDczNDA1MjJ9.DYmOVmgIf2EPmCdXUampKnOI6uEJlnopWkOCcsVJwXYAHPEU1wcD6bVOPGonM98N0pYWXU0k6NADIpvcoQkgRAC8atuOS9iqNduAq43t66fICVC503u6UlnCIwQDiMaGql3M7D3gDLNKVFQcJsyvQ-fax-mOu5bBQyAXEqbbl2SPA0T01_l2DI6j0ahit0cbC8AlJYAsEedncRvutpWNGON6MyGqV-gP4otW1sR-uTsqSM8miHRgr0BatJxzpYI5CVFwBxViXCYfbI_38B_wUmTCSBCGGslTDbZk2C0hqHtEIucTd--Yif1NSUfbVzzAlrv3Adc6-P5-a9Om4_kwsw"

        val simulatedGoogleTokenResponse =
            """
                {
                    "access_token" : "at",
                    "id_token": "$otherGoogleIdToken",
                    "refresh_token" : "some refresh token",
                    "expires_in": 3576,
                    "scope": "openid",
                    "token_type": "Bearer"
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
                install(ContentNegotiation) {
                    jackson {
                        this.registerModule(VavrModule())
                        this.registerModule(KotlinModule())
                    }
                }
                oauthSupportApi.oauthApi()()
            }
        }
    }
}
