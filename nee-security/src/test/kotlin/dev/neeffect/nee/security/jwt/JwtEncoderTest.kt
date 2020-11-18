package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.effects.security.oauth.GoogleOpenIdTest.Companion.sampleGoogleToken
import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.security.oauth.GoogleOpenId
import io.fusionauth.jwt.JWTDecoder
import io.fusionauth.jwt.Signer
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.hmac.HMACSigner
import io.fusionauth.jwt.rsa.RSAVerifier
import io.fusionauth.security.DefaultCryptoProvider
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import io.vavr.collection.HashMap
import io.vavr.control.Try
import io.vavr.kotlin.list


internal class JwtEncoderTest:DescribeSpec({
    describe("jwt encoder") {
        val signer: Signer = HMACSigner.newSHA256Signer("too many secrets")

        val encoder = JwtCoder(jwtTestModule)
        val claims=HashMap.of("roles","admin,nionio")
        it("creates jwt token") {
            val jwt = encoder.createJwt("test subject", claims)
            val encodedJWT = encoder.signJwt(jwt)
            encodedJWT.toString() shouldBe expectedEncodedJwt
        }
        it("decodes jwt token") {
            val encodedJWT = expectedEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.get().subject shouldBe "test subject"
        }
        it("decodes jwt claims") {
            val encodedJWT = expectedEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.get().otherClaims["roles"] shouldBe "admin,nionio"
        }
        it("does not decode bad jwt") {
            val encodedJWT = wrongEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.isLeft shouldBe true
        }


    }
    describe("jwtDecoder") {
        it ("decodes sample google token") {
            val jwtDecoder = JWT.getTimeMachineDecoder(haste.now())
            val verifier = GoogleOpenId.Companion.GoogleKeys.googleVerifier
            val googleToken = jwtDecoder.decode(sampleGoogleToken, verifier)
            googleToken.subject shouldBe "108874454676244700380"
            println(googleToken)
        }
        it ("does not decode outdated google token") {
            val jwtDecoder = JWT.getTimeMachineDecoder(hasteInFuture.now())
            val verifier = GoogleOpenId.Companion.GoogleKeys.googleVerifier

            val googleToken = Try.of { jwtDecoder.decode(sampleGoogleToken, verifier)}
            googleToken.isFailure shouldBe true
        }
    }
}) {
    companion object {
        val testConfig = JwtConfig(1000, "neekt takee", "la secret")

        val hasteInFuture = Haste.TimeSource.withFixedClock(Clock.fixed(Instant.parse("2020-11-20T22:22:03.00Z"), ZoneId.of("Europe/Berlin")))
        val haste = Haste.TimeSource.withFixedClock(Clock.fixed(Instant.parse("2020-10-24T22:22:03.00Z"), ZoneId.of("Europe/Berlin")))
        val jwtTestModule = object : JwtCoderConfigurationModule(testConfig) {
            override val timeProvider: TimeProvider
                get() = HasteTimeProvider(haste)
        }

        const val expectedEncodedJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDM1NzkxMjMsImlhdCI6MTYwMzU3ODEyMywiaXNzIjoibmVla3QgdGFrZWUiLCJzdWIiOiJ0ZXN0IHN1YmplY3QiLCJyb2xlcyI6ImFkbWluLG5pb25pbyJ9.Gf9vlhBncjndb0fG91vfT8Ah0xAq7jKq_r0dDJC-4bo"
        const val wrongEncodedJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDM2NzkxMjMsImlhdCI6MTYwMzU3ODEyMywiaXNzIjoibmVla3QgdGFrZWUiLCJzdWIiOiJ0ZXN0IHN1YmplY3QiLCJyb2xlcyI6ImFkbWluLG5pb25pbyJ9.Gf9vlhBncjndb0fG91vfT8Ah0xAq7jKq_r0dDJC-4bo"



    }
}






