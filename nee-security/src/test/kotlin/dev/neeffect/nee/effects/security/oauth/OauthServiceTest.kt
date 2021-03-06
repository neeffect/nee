package dev.neeffect.nee.effects.security.oauth

import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.test.getLeft
import dev.neeffect.nee.security.oauth.OauthProviderName
import dev.neeffect.nee.security.oauth.OauthService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveMinLength
import io.kotest.matchers.types.shouldBeTypeOf
import io.vavr.kotlin.some

internal class OauthServiceTest : DescribeSpec({

    describe("oauth service") {
        val testModule = GoogleOpenIdTest.createTestModule()
        val service = OauthService(testModule)
        describe("login to google") {
            val result = service.login(
                "acode",
                GoogleOpenIdTest.preservedState,
                "http://localhost:8080",
                OauthProviderName.Google
            )
                .perform(Unit)

            it("should be successful") {
                result.get().encodedToken shouldHaveMinLength 20
            }
            //TODO - actually think what is subject here
            it("should contain user id in token") {
                val jwt = result.get().encodedToken
                testModule.jwtConfigModule.jwtCoder.decodeJwt(jwt)
                    .get().subject shouldBe "ba419d35-0dfe-8af7-aee7-bbe10c45c028"
            }
            it("should contain user name") {
                result.get().displayName shouldBe some("Jarek Ratajski")
            }
            it("encoded token should contain user name") {
                val jwt = result.get().encodedToken
                service.decodeUser(jwt).get().displayName shouldBe "Jarek Ratajski"
            }
        }
        it("should create oauth api call url") {
            val url = service.generateApiCall(OauthProviderName.Google, "http://globalpost.any/mypath")
            url shouldBe some(expectedUrlCall)
        }
        describe("login with bad state") {
            val result = service.login("acode", "really bad state", "http://localhost:8080", OauthProviderName.Google)
                .perform(Unit)
            it("should fail") {
                result.getLeft().shouldBeTypeOf<SecurityErrorType.MalformedCredentials>()
            }
        }


    }

}) {
    companion object {
        //notice - this is volatile and may change if scenario is changed (depends on random sequence)
        const val expectedUrlCall =
            "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=testId&scope=openid%20profile%20email%20https://www.googleapis.com/auth/user.organization.read&redirect_uri=http://globalpost.any/mypath&state=5DwIT0u7K/GDne5GbYUstQ==@Z0GCZibsGi2m32XO2BO3J/i7GyncfvHwADR7y2lZxm9NoVYGNZ2k7reTDuZ4GLbdE4lXZMA8bG3vHLM9ZDYo3XQZ7nXUZa+/FeO12M2ire7gYCHOLf+V/4Xl/hbetiGHT7z5FnzlrtVlG6wQZaZJBn2NdzuLZ4grQHY8jThP8nI=&login_hint=jsmith@example.com&nonce=0.6655489"
    }
}
