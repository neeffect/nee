package dev.neeffect.nee.ctx.web

import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.toFuture
import dev.neeffect.nee.security.InMemoryUserRealm
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.vavr.control.Option.none
import io.vavr.control.Option.some

internal class BasicAuthProviderTest : DescribeSpec({
    describe("basic auth") {
        val userRealm = InMemoryUserRealm<String, String>().withPassword("test1", "test2".toCharArray())
            .withRole("test1", "unfixer")
        describe("with correct auth header") {
            val provider = BasicAuthProvider(some("Basic dGVzdDE6dGVzdDI="), userRealm)
            it("should find role context") {
                provider.getSecurityContext()
                    .toFuture().get().get()
                    .getCurrentUser().toFuture().get().get() should be("test1")
            }
            it("should find role") {
                provider.getSecurityContext()
                    .toFuture().get().get()
                    .hasRole("unfixer") should be(true)
            }
            it("should reject unknown role") {
                provider.getSecurityContext()
                    .toFuture().get().get()
                    .hasRole("admin") should be(false)
            }
        }
        describe("with no header") {
            val provider = BasicAuthProvider(none(), userRealm)
            it("should not find user") {
                provider.getSecurityContext()
                    .toFuture().get().get()
                    .getCurrentUser().toFuture().get().swap().get() shouldBe (SecurityErrorType.UnknownUser)
            }
            it("should have no roles") {
                provider.getSecurityContext()
                    .toFuture().get().get()
                    .hasRole("unfixer") should be(false)
            }
        }
        describe("with broken header") {
            val provider = BasicAuthProvider(some("Basic dGVzd!@sa222DE6dGVzdDI="), userRealm)
            it("should result in error user") {
                provider.getSecurityContext()
                    .toFuture().get().swap().get()
                    .shouldBeTypeOf<SecurityErrorType.MalformedCredentials>()
            }
            it("should have no roles") {
                provider.getSecurityContext()
                    .toFuture().get().swap().get()
                    .shouldBeTypeOf<SecurityErrorType.MalformedCredentials>()
            }
        }
    }
})
