package dev.neeffect.nee.web.test

import com.fasterxml.jackson.databind.ObjectMapper
import dev.neeffect.nee.ctx.web.WebContextProvider
import dev.neeffect.nee.ctx.web.pure.InitialRouting
import dev.neeffect.nee.ctx.web.pure.Routing
import dev.neeffect.nee.ctx.web.pure.RoutingDef
import dev.neeffect.nee.effects.tx.TxProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.routing.routing

fun <R, G : TxProvider<R, G>> testApplication(
    mapper: ObjectMapper,
    webContextProvider: WebContextProvider<R, G>,
    aRouting: (Routing<R, G>) -> RoutingDef<R, G>
): Application.() -> Unit = {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(mapper))
    }
    val initialRouting = InitialRouting<R, G>()
    routing {
        aRouting(initialRouting).buildRoute(this, webContextProvider)
    }
}
