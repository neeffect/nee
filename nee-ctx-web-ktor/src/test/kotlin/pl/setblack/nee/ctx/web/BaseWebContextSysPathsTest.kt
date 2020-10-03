package pl.setblack.nee.ctx.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import pl.setblack.nee.ctx.web.support.EmptyTestContext

internal class BaseWebContextSysPathsTest : DescribeSpec({
    describe("sys routing paths") {
        val engine = TestApplicationEngine(createTestEnvironment())
        engine.start(wait = false)
        engine.application.sysApp(EmptyTestContext.contexProvider)
        it ("returns ok healthCheck") {
            val status = engine.handleRequest(HttpMethod.Get, "/healthCheck").response.status()
            status shouldBe (HttpStatusCode.OK)
        }
    }
}) {

}

fun  Application.sysApp(ctx: WebContextProvider<*,*>) {
    routing (ctx.healthCheck())
}
