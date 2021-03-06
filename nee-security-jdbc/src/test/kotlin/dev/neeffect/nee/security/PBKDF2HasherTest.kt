package dev.neeffect.nee.security

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class PBKDF2HasherTest : DescribeSpec({
    describe("pbk2hasher") {
        val hasher = PBKDF2Hasher()
        describe("password with some salt") {
            val salt = ByteArray(16) { 0xCA.toByte() }
            val password = "very secret"
            val hash = hasher.hashPassword(password.toCharArray(), salt)
            it("hashes to expected value") {
                BigInteger(1, hash).toString(16) shouldBe "bb70b13aa04c8e21cbaaa54903cb030b"
            }
        }
    }
})
