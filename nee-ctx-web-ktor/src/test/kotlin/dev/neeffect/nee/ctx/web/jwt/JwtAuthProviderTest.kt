package dev.neeffect.nee.ctx.web.jwt

import dev.neeffect.nee.ctx.web.oauth.OauthSupportApiTest
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.test.getLeft
import dev.neeffect.nee.security.UserRole
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.vavr.kotlin.none
import io.vavr.kotlin.option

internal class JwtAuthProviderTest : DescribeSpec({
    describe("using standard config") {
        val jwtConfigModule = OauthSupportApiTest.oauthConfigModule.jwtConfigModule
        describe("no auth header ") {
            val provider = JwtAuthProvider(none(), jwtConfigModule)
            provider.getSecurityContext().getLeft() shouldBe SecurityErrorType.NoSecurityCtx
        }
        describe("broken auth header ") {
            val provider = JwtAuthProvider("broken code".option(), jwtConfigModule)
            provider.getSecurityContext().getLeft().shouldBeTypeOf<SecurityErrorType.MalformedCredentials>()
        }

        describe("broken jwt token ") {
            val provider = JwtAuthProvider("Bearer nonsensecode".option(), jwtConfigModule)
            provider.getSecurityContext().getLeft().shouldBeTypeOf<SecurityErrorType.MalformedCredentials>()
        }

        describe("correct jwt token ") {
            val provider = JwtAuthProvider("Bearer $exampleJwtToken".option(), jwtConfigModule)
            it("has role oauthUser") {
                provider.getSecurityContext().get().hasRole(UserRole("oauthUser")) shouldBe true
            }
            it("does not have role admin") {
                provider.getSecurityContext().get().hasRole(UserRole("admin")) shouldBe false
            }

            it("has user name") {
                provider.getSecurityContext().get().getCurrentUser().get().displayName shouldBe "Jarek Ratajski"
            }

        }
    }
}) {
    companion object {
        const val exampleJwtToken =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDM1NzkxMjMsImlhdCI6MTYwMzU3ODEyMywiaXNzIjoidGVzdCIsInN1YiI6ImJhNDE5ZDM1LTBkZmUtOGFmNy1hZWU3LWJiZTEwYzQ1YzAyOCIsImxvZ2luIjoiZ29vZ2xlOjEwODg3NDQ1NDY3NjI0NDcwMDM4MCIsImRpc3BsYXlOYW1lIjoiSmFyZWsgUmF0YWpza2kiLCJpZCI6ImJhNDE5ZDM1LTBkZmUtOGFmNy1hZWU3LWJiZTEwYzQ1YzAyOCIsInJvbGVzIjoib2F1dGhVc2VyIn0.5ZsttU5WgJKRFTxBFso4ETqrc-loViGxku539hI5SvY"
    }
}
