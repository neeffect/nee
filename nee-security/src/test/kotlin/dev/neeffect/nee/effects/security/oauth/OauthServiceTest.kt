package dev.neeffect.nee.effects.security.oauth

import dev.neeffect.nee.security.oauth.OauthProviders
import dev.neeffect.nee.security.oauth.OauthService
import io.kotest.core.spec.style.DescribeSpec

class OauthServiceTest :DescribeSpec({

    describe("oauth service") {
        val service = OauthService(GoogleOpenIdTest.testModule)
        it("logs to google") {
            val result = service.login("acode", GoogleOpenIdTest.preservedState, OauthProviders.Google.providerName)
                .perform(Unit)(Unit)
            //result.
        }
    }

}) {

}
