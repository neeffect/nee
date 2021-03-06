package dev.neeffect.nee.ctx.web

import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.support.EmptyTestContext
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import kotlinx.coroutines.newFixedThreadPoolContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

fun Application.slowApp() {

    routing {
        get("/slow") {
            Thread.sleep(100)
            println("waited 100 ${System.currentTimeMillis()} ${Thread.currentThread().name}")
            call.respondText { "ok" }
        }
        get("/fast") {
            val wc = EmptyTestContext.contexProvider.create(call)
            val result = Nee.with(EmptyTestContext.contexProvider.fx().async) {
                Thread.sleep(100)
                "ok"
            }.perform(wc)
            wc.serveMessage(result)
        }
    }
}

class KtorThreadingModelTest : BehaviorSpec({

    Given("ktor app") {

        val engine = TestApplicationEngine(createTestEnvironment()) {
            this.dispatcher = newFixedThreadPoolContext(2, "test ktor dispatcher")
        }
        engine.start(wait = false)
        engine.application.slowApp()
        When("slow req bombarded with 100 threads") {
            val countdown = CountDownLatch(reqs)
            val initTime = System.currentTimeMillis()
            (0..reqs).forEach {

                reqExecutor.submit {
                    engine.handleRequest(HttpMethod.Get, "/slow").response.content
                    countdown.countDown()
                }
            }
            then("slow is slow") {
                countdown.await()
                val totalTime = System.currentTimeMillis() - initTime
                println(totalTime)
                totalTime shouldBeGreaterThan 2000
            }
        }
        When("fast req bombarded with 100 threads") {
            val countdown = CountDownLatch(reqs)
            val initTime = System.currentTimeMillis()
            (0..reqs).forEach {
                reqExecutor.submit {
                    engine.handleRequest(HttpMethod.Get, "/fast").response.content
                    countdown.countDown()
                }
            }
            then("fast is faster") {
                countdown.await()
                val totalTime = System.currentTimeMillis() - initTime
                println(totalTime)
                totalTime shouldBeLessThan 2000
            }
        }
    }
}) {
    companion object {
        val reqs = 100
        val reqExecutor = Executors.newFixedThreadPool(reqs)

        init {
            println("ok")
        }
    }
}
