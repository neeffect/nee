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
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

sealed class Routing<R, G : TxProvider<R, G>> {
    abstract operator fun plus(other: RoutingDef<R, G>): RoutingDef<R, G>
}

class InitialRouting<R, G : TxProvider<R, G>> : Routing<R, G>() {
    override fun plus(other: RoutingDef<R, G>) = other
}

data class RoutingDef<R, G : TxProvider<R, G>>(internal val r: (Route, WebContextProvider<R, G>) -> Unit) :
    Routing<R, G>() {
    override operator fun plus(other: RoutingDef<R, G>): RoutingDef<R, G> = RoutingDef<R, G> { route, ctx ->
        r(route, ctx)
        other.r(route, ctx)
    }

    fun buildRoute(route: Route, ctx: WebContextProvider<R, G>) =
        r(route, ctx)
}

class RouteBuilder<R, G : TxProvider<R, G>>

inline fun <reified A : Any, R, G : TxProvider<R, G>> RouteBuilder<R, G>.get(
    path: String = "",
    crossinline f: (ApplicationCall) -> Nee<WebContext<R, G>, Any, A>
): RoutingDef<R, G> =
    RoutingDef<R, G> { r, ctx ->
        with(r) {
            get(path) {
                val webContext = ctx.create(call)
                webContext.serveMessage(f(call).perform(ctx.create(call)))
            }
        }
    }

inline fun <reified A : Any, R, G : TxProvider<R, G>> RouteBuilder<R, G>.post(
    path: String = "",
    crossinline f: (ApplicationCall) -> Nee<WebContext<R, G>, Any, A>
): RoutingDef<R, G> =
    RoutingDef<R, G> { r, ctx ->
        with(r) {
            post(path) {
                val webContext = ctx.create(call)
                webContext.serveMessage(f(call).perform(ctx.create(call)))
            }
        }
    }

inline fun <reified A : Any, R, G : TxProvider<R, G>> RouteBuilder<R, G>.delete(
    path: String = "",
    crossinline f: (ApplicationCall) -> Nee<WebContext<R, G>, Any, A>
): RoutingDef<R, G> =
    RoutingDef<R, G> { r, ctx ->
        with(r) {
            delete(path) {
                val webContext = ctx.create(call)
                webContext.serveMessage(f(call).perform(ctx.create(call)))
            }
        }
    }

inline fun <reified A : Any, R, G : TxProvider<R, G>> RouteBuilder<R, G>.put(
    path: String = "",
    crossinline f: (ApplicationCall) -> Nee<WebContext<R, G>, Any, A>
): RoutingDef<R, G> =
    RoutingDef<R, G> { r, ctx ->
        with(r) {
            put(path) {
                val webContext = ctx.create(call)
                webContext.serveMessage(f(call).perform(ctx.create(call)))
            }
        }
    }

inline fun <reified A : Any, R, G : TxProvider<R, G>> RouteBuilder<R, G>.patch(
    path: String = "",
    crossinline f: (ApplicationCall) -> Nee<WebContext<R, G>, Any, A>
): RoutingDef<R, G> =
    RoutingDef<R, G> { r, ctx ->
        with(r) {
            patch(path) {
                val webContext = ctx.create(call)
                webContext.serveMessage(f(call).perform(ctx.create(call)))
            }
        }
    }

inline fun <R, G : TxProvider<R, G>> RouteBuilder<R, G>.nested(
    path: String = "",
    crossinline f: (Routing<R, G>) -> RoutingDef<R, G>
): RoutingDef<R, G> =
    RoutingDef<R, G> { r, ctx ->
        with(r) {
            route(path) {
                f(InitialRouting<R, G>()).buildRoute(this, ctx)
            }
        }
    }

fun <R, G : TxProvider<R, G>> startNettyServer(
    port: Int,
    mapper: ObjectMapper,
    webContextProvider: WebContextProvider<R, G>,
    aRouting: (Routing<R, G>) -> RoutingDef<R, G>
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
