package dev.neeffect.nee.ctx.web

import dev.neeffect.nee.ctx.web.support.EmptyTestContextProvider
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.security.SecurityCtx
import dev.neeffect.nee.effects.security.SecurityError
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.vavr.collection.List
import java.util.*
import kotlin.random.Random

internal class BaseWebContextSysPathsTest : DescribeSpec({
    describe("sys routing paths") {
        val engine = TestApplicationEngine(createTestEnvironment())
        engine.start(wait = false)
        engine.application.sysApp(TestSysContext.contexProvider)
        it("returns ok healthCheck") {
            val status = engine.handleRequest(HttpMethod.Get, "/sys/healthCheck").response.status()
            status shouldBe (HttpStatusCode.OK)
        }
        it("returns login OK check") {
            val content = engine.handleRequest(HttpMethod.Get, "/sys/currentUser") {
                this.addHeader(HttpHeaders.Authorization, "testUser ygrek")
            }.response.content
            val user = TestSysContext.contexProvider.jacksonMapper.readValue(content, User::class.java)
            user.login shouldBe ("ygrek")
        }
        it("returns login failed login check") {
            val resp = engine.handleRequest(HttpMethod.Get, "/sys/currentUser")
                .response
            resp.status() shouldBe (HttpStatusCode.Unauthorized)
        }
        it("returns role check ok") {
            val status = engine.handleRequest(
                HttpMethod.Get,
                "/sys/hasRoles?roles=admin"
            ) {
                this.addHeader(HttpHeaders.Authorization, "testUser admin")
            }.response.status()
            status shouldBe (HttpStatusCode.OK)
        }
        it("returns role check failed") {
            val status = engine.handleRequest(
                HttpMethod.Get,
                "/sys/hasRoles?roles=admin"
            ) {
                this.addHeader(HttpHeaders.Authorization, "testUser ygrek")
            }.response.status()
            status shouldBe (HttpStatusCode.Unauthorized)
        }
    }
}) {
    object TestSysContext : EmptyTestContextProvider() {
        val userMatcher = ("""^testUser (\w+)$""").toRegex()
        private val rng = Random(55L)
        override fun security(call: ApplicationCall): SecurityProvider<User, UserRole> =
            object : SecurityProvider<User, UserRole> {

                override fun getSecurityContext(): Out<SecurityError, SecurityCtx<User, UserRole>> =
                    call.request.headers.get(HttpHeaders.Authorization)?.let { authHeader ->
                        userMatcher.find(authHeader)?.let { matchRes ->
                            val userName = matchRes.groupValues[1]
                            val uuid = UUID(rng.nextLong(), rng.nextLong())
                            val user = User(uuid, userName, List.of(UserRole(userName)))
                            Out.right(TestCTX(user))
                        } ?: Out.left(SecurityErrorType.MalformedCredentials("header=$authHeader"))
                    } ?: Out.left(SecurityErrorType.MalformedCredentials("no header"))
            }

        class TestCTX(private val user: User) : SecurityCtx<User, UserRole> {

            override fun getCurrentUser(): Out<SecurityError, User> =
                Out.right(user)

            override fun hasRole(role: UserRole): Boolean = user.roles.contains(role)
        }
    }
}

fun Application.sysApp(ctx: WebContextProvider<*, *>) {
    routing(ctx.sysApi())
}
