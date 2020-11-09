package dev.neeffect.nee.effects.security.oauth

import dev.neeffect.nee.security.oauth.GoogleOpenId
import dev.neeffect.nee.security.oauth.OauthConfig
import dev.neeffect.nee.security.state.ServerVerifier
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths
import java.util.*

internal class GoogleOpenIdTest : DescribeSpec({
    describe("gooogle open id") {
        val googleOpenId = GoogleOpenId(serverVerifier, testOauthConfig, rng)
        it( "generates api call url") {
            val url = googleOpenId.generateApiCall("lokal-post")
            url shouldBe expectedUrl
        }
    }
}) {
    companion object {
        val rng = Random(42L)

        val testOauthConfig = OauthConfig("testId")
        val keyPath = GoogleOpenIdTest::class.java.getResourceAsStream("/keys/testServerKey.bin")
        val serveKeyPair = ServerVerifier.loadKeyPair(keyPath).get()
        val serverVerifier  = ServerVerifier(rng, serveKeyPair)
        val expectedUrl="""
        https://accounts.google.com/o/oauth2/v2/auth?
        response_type=code&
        client_id=testId&
        scope=openid&
        redirect_uri=lokal-post&
        state=NZ1BuveK/g3hu+euKMBFDA==@BTyAoSqsaxQUq11+TWc+cRxvrkK3qcFnNivhjzu5luuoycBhyWoGaz0Z1e7bG4PO1x+onlyNAvDhKzzXpmVm2onC0tHVzRy/0pNBDXCgaeAx/mXmoIxtcMBRPObzjF1ENnu/zZ+jocni8comvOkYf1iqJAhiZNwKaixsirLaF00=&
        login_hint=jsmith@example.com&
        nonce=0.3087194
        """.trimIndent().replace("\n", "")
    }
}
