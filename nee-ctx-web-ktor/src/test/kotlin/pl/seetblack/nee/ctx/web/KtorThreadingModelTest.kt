package pl.seetblack.nee.ctx.web

import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeLessThan
import io.kotlintest.specs.BehaviorSpec
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import kotlinx.coroutines.IO_PARALLELISM_PROPERTY_NAME
import pl.setblack.nee.Nee
import pl.setblack.nee.ctx.web.WebContext
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.async.ECProvider
import pl.setblack.nee.effects.async.ExecutorExecutionContext
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.effects.security.SecurityCtx
import pl.setblack.nee.effects.security.SecurityError
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.invalid
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRole
import java.sql.Connection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

fun Application.slowApp() {
    val  myTxProvider  = object :  TxProvider<Connection, JDBCProvider> {
        override fun getConnection(): TxConnection<Connection> = invalid()

        override fun setConnectionState(newState: TxConnection<Connection>): JDBCProvider  =invalid()
    }

    val noSecurity = object : SecurityProvider<User, UserRole> {
        override fun getSecurityContext(): Out<SecurityError, SecurityCtx<User, UserRole>>  = invalid()
    }

    val serverExecutor = Executors.newFixedThreadPool(KtorThreadingModelTest.reqs)
    val ec = ExecutorExecutionContext(serverExecutor)

    routing {
        get("/slow") {
            Thread.sleep(100)
            call.respondText { "ok" }
        }
        get("/fast") {
            val wc = WebContext(myTxProvider, noSecurity, ECProvider(ec),call)
            val result = Nee.constP(WebContext.Effects.async) {
                Thread.sleep(100)
                "ok"
            }.perform(wc)(Unit)
            wc.serveMessage(result)
        }
    }
}

internal class KtorThreadingModelTest : BehaviorSpec({
    System.setProperty(IO_PARALLELISM_PROPERTY_NAME, "2")
    Given("ktor app") {
        val engine = TestApplicationEngine(createTestEnvironment())
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
            Then("slow is slow") {
                countdown.await()
                val totalTime = System.currentTimeMillis() - initTime
                println(totalTime)
                totalTime shouldBeGreaterThan   2000
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
            Then("fast is faster") {
                countdown.await()
                val totalTime = System.currentTimeMillis() - initTime
                println(totalTime)
                totalTime shouldBeLessThan  2000
            }
        }
    }
}) {
    companion object {
        val reqs = 100
        val reqExecutor = Executors.newFixedThreadPool(reqs)

    }
}