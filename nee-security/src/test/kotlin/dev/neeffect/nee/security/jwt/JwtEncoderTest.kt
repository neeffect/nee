package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import io.fusionauth.jwt.Signer
import io.fusionauth.jwt.hmac.HMACSigner
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import io.vavr.collection.HashMap


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
            jwt.subject shouldBe "test subject"
        }
        it("decodes jwt claims") {
            val encodedJWT = expectedEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.otherClaims["roles"] shouldBe "admin,nionio"
        }

    }
}) {
    companion object {
        val testConfig = JwtConfig(1000, "neekt takee", "la secret")
        val haste = Haste.TimeSource.withFixedClock(
            Clock.fixed(Instant.parse("2020-10-24T22:22:03.00Z"), ZoneId.of("Europe/Berlin"))
        )
        val jwtTestModule = object : JwtCoderConfigurationModule(testConfig) {
            override val timeProvider: TimeProvider
                get() = HasteTimeProvider(haste)
        }

        const val expectedEncodedJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDM1NzkxMjMsImlhdCI6MTYwMzU3ODEyMywiaXNzIjoibmVla3QgdGFrZWUiLCJzdWIiOiJ0ZXN0IHN1YmplY3QiLCJyb2xlcyI6ImFkbWluLG5pb25pbyJ9.Gf9vlhBncjndb0fG91vfT8Ah0xAq7jKq_r0dDJC-4bo"
    }
}




