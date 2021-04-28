package dev.neeffect.nee.web.test

import dev.neeffect.nee.ctx.web.JDBCBasedWebContextProvider
import dev.neeffect.nee.effects.jdbc.JDBCConfig
import dev.neeffect.nee.effects.jdbc.JDBCProvider
import io.ktor.application.Application
import io.ktor.server.engine.ApplicationEngineEnvironment
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.createTestEnvironment
import kotlin.coroutines.EmptyCoroutineContext

open class TestWebContextProvider : JDBCBasedWebContextProvider() {

    open val testEnv: ApplicationEngineEnvironment by lazy { createTestEnvironment() }
    open val testApplication by lazy { Application(testEnv) }
    open val testCallConstrucor
            by lazy { { TestApplicationCall(testApplication, false, true, EmptyCoroutineContext) } }

    val jdbcConfig: JDBCConfig by lazy {
        JDBCConfig(
            driverClassName = "org.h2.Driver",
            url = "jdbc:h2:mem:test",
            user = "sa",
            password = ""
        )
    }
    override val jdbcProvider: JDBCProvider by lazy {
        JDBCProvider(jdbcConfig)
    }

    fun testCtx(reqConfig: (TestApplicationRequest) -> Unit = {}) = testCallConstrucor().let { appCall ->
        reqConfig(appCall.request)
        super.create(appCall)
    }
}
