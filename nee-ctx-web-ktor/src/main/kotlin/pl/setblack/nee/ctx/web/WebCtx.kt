package pl.setblack.nee.ctx.web


import pl.setblack.nee.effects.cache.CacheEffect
import pl.setblack.nee.effects.cache.CacheProvider
import pl.setblack.nee.effects.cache.NaiveCacheProvider
import pl.setblack.nee.effects.jdbc.JDBCProvider
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxEffect
import pl.setblack.nee.effects.tx.TxProvider
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRole
import java.sql.Connection

class WebContext(
    val jdbcProvider: TxProvider<Connection, JDBCProvider>,
    val cacheProvider: CacheProvider,
    val securityProvider: SecurityProvider<User, UserRole>)
    : TxProvider<Connection, WebContext>,
    CacheProvider by cacheProvider,
    SecurityProvider<User, UserRole> by securityProvider{
    override fun getConnection(): TxConnection<Connection>  = jdbcProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<Connection>): WebContext =
        WebContext(
            jdbcProvider.setConnectionState(newState),
            cacheProvider,
            securityProvider)
}
object WebEffects {
    val jdbc = TxEffect<Connection, WebContext>()
    val cache = CacheEffect<WebContext, Nothing>(NaiveCacheProvider())
}