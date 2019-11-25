package pl.setblack.nee.ctx.web


import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.request.header
import io.ktor.response.respond
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

    fun <P> serveText(businessFunction: ANee<WebContext, P, String>, param: P) {
        val result = businessFunction.perform(this)(param)
        result.onComplete { outcome ->
            val message = outcome.bimap({ errorResult ->
                TextContent(
                    text = "error:" + errorResult.toString(),
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.InternalServerError
                )
            }, { regularResult ->
                TextContent(
                    text = regularResult,
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.OK
                )
            }).merge()
            runBlocking { applicationCall.respond(message) }
        }
    }

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


    }
}

object WebCtxEffects {
    val jdbc = TxEffect<Connection, WebContext>().anyError()
    val cache = CacheEffect<WebContext, Nothing>(CaffeineProvider()).anyError()


}