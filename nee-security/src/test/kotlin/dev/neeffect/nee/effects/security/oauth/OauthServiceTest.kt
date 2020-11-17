package dev.neeffect.nee.effects.security.oauth

import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.security.oauth.OauthProviders
import dev.neeffect.nee.security.oauth.OauthService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OauthServiceTest : DescribeSpec({

    describe("oauth service") {
        val service = OauthService(GoogleOpenIdTest.testModule)
        it("logs to google") {
            val result = service.login("acode", GoogleOpenIdTest.preservedState, OauthProviders.Google.providerName)
                .perform(Unit)(Unit)
            result.get().encodedToken shouldBe "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDM1NzkxMjMsImlhdCI6MTYwMzU3ODEyMywiaXNzIjoidGVzdCIsInN1YiI6InRvZG8ifQ.82fsKb_7B2fFcY4DLCM-YKLwllUxZYsYEcake1YQR_Y"
        }
    }

})
