package pl.setblack.nee.ctx.web

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import pl.setblack.nee.UANee
import pl.setblack.nee.effects.jdbc.JDBCConfig

/**
 * Generic app context.
 */
interface ApplicationContextProvider<CTX, LOCAL> {
    suspend fun serve( businessFunction: UANee<CTX, Any>, localParam : LOCAL)
}

/**
 * Web application context.
 */
//class WebApplicationContextProvider(private val jdbcConfig : JDBCConfig) : ApplicationContextProvider<WebContext, ApplicationCall> {
//    override suspend fun serve(businessFunction: UANee<WebContext, Any>, localParam: ApplicationCall) {
//        WebContext.create(jdbcConfig, localParam).serveMessage(businessFunction, Unit)
//    }
//}
