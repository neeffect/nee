package dev.neeffect.nee.effects.security.oauth

import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.security.oauth.OauthProviders
import dev.neeffect.nee.security.oauth.OauthService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveMinLength
import io.vavr.kotlin.some

internal class OauthServiceTest : DescribeSpec({

    describe("oauth service") {
        val service = OauthService(GoogleOpenIdTest.testModule)
        describe("login to google") {
            val result = service.login("acode", GoogleOpenIdTest.preservedState, OauthProviders.Google.providerName)
                .perform(Unit)(Unit)

            it ("should be successful") {
                result.get().encodedToken shouldHaveMinLength 20
            }
            //TODO - actually think what is subject here
            it ("should contain user id in token") {
                val jwt = result.get().encodedToken
                GoogleOpenIdTest.testModule.jwtCoder.decodeJwt(jwt).get().subject shouldBe "108874454676244700380"
            }
            it ("should contain user name") {
                result.get().displayName shouldBe some("Jarek Ratajski")
            }
        }
    }

})
