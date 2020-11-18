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
            val result = service.login("acode", GoogleOpenIdTest.preservedState, OauthProviders.Google)
                .perform(Unit)(Unit)

            it ("should be successful") {
                result.get().encodedToken shouldHaveMinLength 20
            }
            //TODO - actually think what is subject here
            it ("should contain user id in token") {
                val jwt = result.get().encodedToken
                GoogleOpenIdTest.testModule.jwtCoder.decodeJwt(jwt).get().subject shouldBe "ba419d35-0dfe-8af7-aee7-bbe10c45c028"
            }
            it ("should contain user name") {
                result.get().displayName shouldBe some("Jarek Ratajski")
            }
            it ("endoded token should contain user name") {
                val jwt = result.get().encodedToken
                service.decodeUser(jwt).get().displayName shouldBe "Jarek Ratajski"
            }
        }
    }

})
