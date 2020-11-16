package dev.neeffect.nee.effects.security.oauth

import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.test.getAny
import dev.neeffect.nee.security.oauth.GoogleOpenId
import dev.neeffect.nee.security.oauth.OauthConfig
import dev.neeffect.nee.security.oauth.OauthConfigModule
import dev.neeffect.nee.security.state.ServerVerifier
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
import io.ktor.http.ParametersImpl
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import java.security.KeyPair
import java.util.*

internal class GoogleOpenIdTest : DescribeSpec({
    describe("gooogle open id") {
        val googleOpenId = GoogleOpenId(testModule)
        it( "generates api call url") {
            val url = googleOpenId.generateApiCall("lokal-post")
            url shouldBe expectedUrl
        }
        it("calls google for tokens") {
            val tokens = googleOpenId.verifyOauthToken("acode")
            tokens.perform(Unit)(Unit).get().idToken shouldBe "someJwt"
        }

        it("return no jwt in case of a bad token") {
            val tokens = googleOpenId.verifyOauthToken("bad code")
            tokens.perform(Unit)(Unit).getAny().isLeft shouldBe true
        }
    }
}) {
    companion object {
        val testRandom = Random(42L)

        val testOauthConfig = OauthConfig("testId", "testSecret")
        val keyPath = GoogleOpenIdTest::class.java.getResourceAsStream("/keys/testServerKey.bin")
        val serveKeyPair = ServerVerifier.loadKeyPair(keyPath).get()

        val preservedState = "NZ1BuveK/g3hu+euKMBFDA==@BTyAoSqsaxQUq11+TWc+cRxvrkK3qcFnNivhjzu5luuoycBhyWoGaz0Z1e7bG4PO1x+onlyNAvDhKzzXpmVm2onC0tHVzRy/0pNBDXCgaeAx/mXmoIxtcMBRPObzjF1ENnu/zZ+jocni8comvOkYf1iqJAhiZNwKaixsirLaF00="

        val expectedUrl="""
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
                            if (code == "acode" ) {
                                val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
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
        val simulatedGoogleTokenResponse =
            """
                {
                    "access_token" : "at",
                    "id_token": "someJwt",
                    "refresh_token" : "some refresh token"
                }
            """.trimIndent()

        val testModule = object : OauthConfigModule(testOauthConfig) {
            override val rng: Random
                get() = testRandom
            override val keyPair: KeyPair
                get() = serveKeyPair
            override val httpClient: HttpClient
                get() = testHttpClient
        }

    }
}
