package dev.neeffect.nee.ctx.web

import dev.neeffect.nee.ANee

/**
 * Generic app context.
 */
interface ApplicationContextProvider<CTX, LOCAL> {
    suspend fun serve(businessFunction: ANee<CTX, Any>, localParam: LOCAL)
}
