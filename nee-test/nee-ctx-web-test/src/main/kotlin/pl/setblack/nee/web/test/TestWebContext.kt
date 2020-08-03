package pl.setblack.nee.web.test

import io.ktor.application.Application
import io.ktor.server.engine.ApplicationEngineEnvironment
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.createTestEnvironment
import pl.setblack.nee.ctx.web.JDBCBasedWebContext
import pl.setblack.nee.effects.jdbc.JDBCConfig
import kotlin.coroutines.EmptyCoroutineContext

open class TestWebContext : JDBCBasedWebContext(){

    open val testEnv: ApplicationEngineEnvironment by  lazy { createTestEnvironment() }
    open val testApplication by lazy {Application(testEnv)}
    open val testCallConstrucor
            by lazy { {TestApplicationCall(testApplication, false, EmptyCoroutineContext)}}

    override val jdbcConfig : JDBCConfig by lazy {
        JDBCConfig(
            driverClassName = "org.h2.Driver",
            url = "jdbc:h2:mem:test",
            user = "sa",
            password = ""
        )
    }

    fun testCtx(reqConfig: (TestApplicationRequest)->Unit = {})
                = testCallConstrucor().let {appCall->
                        reqConfig(appCall.request)
                        super.create(appCall)
                    }

}