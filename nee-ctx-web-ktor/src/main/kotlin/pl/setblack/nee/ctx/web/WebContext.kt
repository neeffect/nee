package pl.setblack.nee.ctx.web


import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.request.header
import io.ktor.response.respond
import io.vavr.jackson.datatype.VavrModule
import io.vavr.kotlin.option
import kotlinx.coroutines.runBlocking
import pl.setblack.nee.ANee
import pl.setblack.nee.anyError
import pl.setblack.nee.effects.cache.CacheEffect
import pl.setblack.nee.effects.cache.caffeine.CaffeineProvider
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxEffect
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.merge
import pl.setblack.nee.security.DBUserRealm
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRole
import java.sql.Connection

class WebContext(
    private val jdbcProvider: TxProvider<Connection, JDBCProvider>,
    private val securityProvider: SecurityProvider<User, UserRole>,
    private val applicationCall: ApplicationCall
) : TxProvider<Connection, WebContext>,
    SecurityProvider<User, UserRole> by securityProvider {
    override fun getConnection(): TxConnection<Connection> = jdbcProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<Connection>): WebContext =
        WebContext(
            jdbcProvider.setConnectionState(newState),
            securityProvider,
            applicationCall
        )

    fun <P> serveText(businessFunction: ANee<WebContext, P, String>, param: P) =
        businessFunction.perform(this)(param)
            .onComplete { outcome ->
                val message = outcome.bimap(::serveError, { regularResult ->
                    TextContent(
                        text = regularResult,
                        contentType = ContentType.Text.Plain,
                        status = HttpStatusCode.OK
                    )
                }).merge()
                runBlocking { applicationCall.respond(message) }
            }


    fun <P> serveMessage(businessFunction: ANee<WebContext, P, Any>, param: P) = businessFunction.perform(this)(param)
        .onComplete { outcome ->
            val message = outcome.bimap({ serveError(it) as OutgoingContent }, { regularResult ->
                val bytes = jacksonMapper.writeValueAsBytes(regularResult)
                ByteArrayContent(
                    bytes = bytes,
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                ) as OutgoingContent
            }).merge()
            runBlocking { applicationCall.respond(message) }
        }

    private fun serveError(errorResult: Any): TextContent =
        TextContent(
            text = "error:" + errorResult.toString(),
            contentType = ContentType.Text.Plain,
            status = HttpStatusCode.InternalServerError
        )


    companion object {
        fun create(jdbc: JDBCConfig, call: ApplicationCall): WebContext =
            JDBCProvider(jdbc).let { jdbcProvider ->
                val dbUserRealm = DBUserRealm(jdbcProvider)
                val authProvider = BasicAuthProvider<User, UserRole>(
                    call.request.header("Authorization").option(),
                    dbUserRealm
                )
                WebContext(jdbcProvider, authProvider, call)
            }

        internal val jacksonMapper = ObjectMapper()
            .registerModule(VavrModule())

    }
    object Effects {
        val jdbc = TxEffect<Connection, WebContext>().anyError()
        val cache = CacheEffect<WebContext, Nothing>(CaffeineProvider()).anyError()
    }
}