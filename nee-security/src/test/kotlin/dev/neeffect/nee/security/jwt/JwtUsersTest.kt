package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.vavr.kotlin.list
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*

internal class JwtUsersCoderTest : DescribeSpec({
    describe("jwtuserscoder") {
        val jwtUsersCoder = JwtUsersCoder(jwtTestModule.jwtCoder, SimpleUserCoder())
        val uuid = UUID(0, 1)
        val user = User(
            uuid,
            "badmin",
            list(
                UserRole("editor"),
                UserRole("reader")
            ),
            "mirek"
        )
        val jwt = jwtUsersCoder.encodeUser(user)
        describe("decoded user") {
            val decodedUser = jwtUsersCoder.decodeUser(jwt)
            it("has id") {
                decodedUser.get().id shouldBe uuid
            }
            it("has login") {
                decodedUser.get().login shouldBe "badmin"
            }
            it("has role in object") {
                decodedUser.get().roles shouldContain UserRole("reader")
            }
            it("has given role ") {
                jwtUsersCoder.hasRole(decodedUser.get(), UserRole("reader")) shouldBe true
            }
            it("has displayName") {
                decodedUser.get().displayName shouldBe "mirek"
            }

        }

    }
}) {
    companion object {
        val testConfig = JwtConfig(1000, "neekt takee", "la secret")
        val haste = Haste.TimeSource.withFixedClock(
            Clock.fixed(Instant.parse("2020-10-24T22:22:03.00Z"), ZoneId.of("Europe/Berlin"))
        )
        val jwtTestModule = object : JwtConfigurationModule<User, UserRole>(
            testConfig,
            HasteTimeProvider(haste)
        ) {
            override val userCoder: UserCoder<User, UserRole> = SimpleUserCoder()
        }
    }
}
