package pl.setblack.nee.ctx.web


import io.ktor.application.ApplicationCall
import io.ktor.request.header
import io.vavr.kotlin.option
import pl.setblack.nee.effects.cache.CacheEffect
import pl.setblack.nee.effects.cache.CacheProvider
import pl.setblack.nee.effects.cache.NaiveCacheProvider
import pl.setblack.nee.effects.cache.caffeine.CaffeineProvider
import pl.setblack.nee.effects.jdbc.JDBCConfig
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxEffect
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.security.DBUserRealm
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRole
import java.sql.Connection

class WebContext(
    private val jdbcProvider: TxProvider<Connection, JDBCProvider>,
    private val cacheProvider: CacheProvider,
    private val securityProvider: SecurityProvider<User, UserRole>
) : TxProvider<Connection, WebContext>,
    CacheProvider by cacheProvider,
    SecurityProvider<User, UserRole> by securityProvider {
    override fun getConnection(): TxConnection<Connection> = jdbcProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<Connection>): WebContext =
        WebContext(
            jdbcProvider.setConnectionState(newState),
            cacheProvider,
            securityProvider
        )

    companion object {
        fun create(jdbc: JDBCConfig, call:ApplicationCall ): WebContext =
            JDBCProvider(jdbc).let { jdbcProvider ->
                val dbUserRealm = DBUserRealm(jdbcProvider)
                val authProvider = BasicAuthProvider<User, UserRole>(
                    call.request.header("Authorization").option(),
                    dbUserRealm)
                WebContext(jdbcProvider, CaffeineProvider(), authProvider) //TODO here we have some very bad cache (ewch time new instance)
            }
    }
}

object WebCtxEffects {
    val jdbc = TxEffect<Connection, WebContext>()
    val cache = CacheEffect<WebContext, Nothing>(NaiveCacheProvider())
}