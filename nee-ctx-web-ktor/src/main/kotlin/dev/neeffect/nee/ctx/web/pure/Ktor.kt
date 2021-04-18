package dev.neeffect.nee.ctx.web.pure

import com.fasterxml.jackson.databind.ObjectMapper
import dev.neeffect.nee.IO
import dev.neeffect.nee.Nee
import dev.neeffect.nee.ctx.web.WebContext
import dev.neeffect.nee.ctx.web.WebContextProvider
import dev.neeffect.nee.effects.tx.TxProvider
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

sealed class Routing<R, G : TxProvider<R, G>> {
    abstract operator fun plus(other: Routing<R, G>): Routing<R, G>
    abstract fun buildRoute(route: Route, ctx: WebContextProvider<R, G>): Unit
}

class InitialRouting<R, G : TxProvider<R, G>> : Routing<R, G>() {
    override fun plus(other: Routing<R, G>) = other
    override fun buildRoute(route: Route, ctx: WebContextProvider<R, G>) =
        TODO()
}

data class RoutingDef<R, G : TxProvider<R, G>>(internal val r: (Route, WebContextProvider<R, G>) -> Unit) :
    Routing<R, G>() {
    override operator fun plus(other: Routing<R, G>): Routing<R, G> = when (other) {
        is RoutingDef<*, *> -> RoutingDef<R, G> { route, ctx ->
            r(route, ctx)
            (other as RoutingDef<R, G>).r(route, ctx)
        }
        else -> TODO()
    }

    override fun buildRoute(route: Route, ctx: WebContextProvider<R, G>) =
        r(route, ctx)
}

inline fun <reified A : Any, R, G : TxProvider<R, G>> aget(
    path: String = "",
    crossinline f: (ApplicationCall) -> Nee<WebContext<R, G>, Any, A>
): Routing<R, G> =
    RoutingDef<R, G> { r, ctx ->
        with(r) {
            get(path) {
                val webContext = ctx.create(call)
                webContext.serveMessage(f(call).perform(ctx.create(call)))
            }
        }
    }

fun <R, G : TxProvider<R, G>> startNettyServer(
    port: Int,
    mapper: ObjectMapper,
    webContextProvider: WebContextProvider<R, G>,
    aRouting: (Routing<R, G>) -> Routing<R, G>
): IO<Unit> =
    Nee.pure {
        embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(mapper))
            }
            val initialRouting = InitialRouting<R, G>()
            routing {
                aRouting(initialRouting).buildRoute(this, webContextProvider)
            }
        }.start(wait = true)
    }
