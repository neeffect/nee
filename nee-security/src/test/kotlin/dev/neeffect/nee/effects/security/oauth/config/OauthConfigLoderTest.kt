package dev.neeffect.nee.effects.security.oauth.config

import dev.neeffect.nee.security.oauth.OauthProviderName
import dev.neeffect.nee.security.oauth.config.OauthConfigLoder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

class OauthConfigLoderTest : DescribeSpec({
    describe("oauth config loader") {

        val confFolder = Paths.get(OauthConfigLoderTest::class.java.getResource("/conf").toURI())
        val configLoader = OauthConfigLoder(confFolder)
        it("should load jwtConf") {
            val jwt = configLoader.loadJwtConfig()
            jwt.get().issuer shouldBe "test"
        }
        it("should load oauthConf") {
            val oauth = configLoader.loadOauthConfig()
            oauth.get().getClientSecret(OauthProviderName.Google) shouldBe "googleClientSecret"
        }
    }

})
