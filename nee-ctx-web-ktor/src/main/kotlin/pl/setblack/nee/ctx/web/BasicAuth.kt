package pl.setblack.nee.ctx.web

import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.vavr.control.Option
import io.vavr.kotlin.option
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.Out.Companion.left
import pl.setblack.nee.effects.security.SecurityCtx
import pl.setblack.nee.effects.security.SecurityError
import pl.setblack.nee.effects.security.SecurityErrorType
import pl.setblack.nee.effects.security.SecurityProvider
import pl.setblack.nee.security.UserRealm
import java.nio.charset.Charset
import java.util.*

/**
 * Basic auth implementation.
 *
 * This is not very secure type of credential delivery.
 * Use JWT or other method if possible.
 */
object BasicAuth {
    val authorizationHeader = "Authorization"

    /**
     * Context for basic auth check.
     */
    class BasicAuthCtx<USERID, ROLE>(private val userRealm: UserRealm<USERID, ROLE>) {
        fun createSecurityProviderFromRequest(request: ApplicationRequest)
                : SecurityProvider<USERID, ROLE> = BasicAuthProvider<USERID, ROLE>(
            request.header(authorizationHeader).option(), userRealm)
    }
}

class BasicAuthProvider<USERID, ROLE>(
    private val headerVal: Option<String>,
    private val userRealm: UserRealm<USERID, ROLE>
) : SecurityProvider<USERID, ROLE> {
    private val base64Decoder = Base64.getDecoder()
    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USERID, ROLE>> =
        headerVal.map { baseAuth: String ->
            val decodedAut = base64Decoder.decode(baseAuth)
            val colonIndex = decodedAut.indexOf(':'.toByte())
            if (colonIndex > 0) {
                val login = decodedAut.sliceArray(0 until colonIndex).toString(Charset.forName("UTF-8"))
                val pass = decodedAut.sliceArray(colonIndex+1 until decodedAut.size)
                    .toCharArray()
                userRealm.loginUser(login, pass).map {user ->
                    pass.fill(0.toChar()) //I know that cleaning password in such insecure protocol is useless
                    Out.right<SecurityError, SecurityCtx<USERID, ROLE>>(UserSecurityContext(user, userRealm))
                }.getOrElse {
                    Out.left<SecurityError, SecurityCtx<USERID, ROLE>>(SecurityErrorType.WrongCredentials(login))
                }
            } else {
                Out.left<SecurityError, SecurityCtx<USERID, ROLE>>(SecurityErrorType.MalformedCredentials("no colon inside header: $baseAuth"))
            }
        }.getOrElse {
            Out.right(AnonymousSecurityContext())
        }

    class AnonymousSecurityContext<USERID, ROLE> : SecurityCtx<USERID, ROLE> {
        override fun getCurrentUser(): Out<SecurityError, USERID> =
            left(SecurityErrorType.UnknownUser)

        override fun hasRole(role: ROLE): Boolean = false
    }

    class UserSecurityContext<USERID, ROLE>(
        private val user : USERID,
        private val userRealm: UserRealm<USERID, ROLE>) : SecurityCtx<USERID, ROLE> {
        override fun getCurrentUser(): Out<SecurityError, USERID> = Out.right(user)

        override fun hasRole(role: ROLE): Boolean  =
            userRealm.hasRole(user, role)
    }
}


internal fun ByteArray.toCharArray() = CharArray(this.size).also {
    chars ->
    for (index in this.indices) {
        chars[index] = this[index].toChar()
    }
}