package dev.neeffect.nee.ctx.web

import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.Out.Companion.left
import dev.neeffect.nee.effects.security.SecurityCtx
import dev.neeffect.nee.effects.security.SecurityError
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.security.UserRealm
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.vavr.control.Option
import io.vavr.control.Try
import io.vavr.kotlin.option
import java.nio.charset.Charset
import java.util.Base64

/**
 * Basic auth implementation.
 *
 * This is not very secure type of credential delivery.
 * Use JWT or other method if possible.
 */
object BasicAuth {
    const val authorizationHeader = "Authorization"

    /**
     * Context for basic auth check.
     */
    class BasicAuthCtx<USERID, ROLE>(private val userRealm: UserRealm<USERID, ROLE>) {
        fun createSecurityProviderFromRequest(request: ApplicationRequest): SecurityProvider<USERID, ROLE> =
            BasicAuthProvider<USERID, ROLE>(
                request.header(authorizationHeader).option(), userRealm
            )
    }
}

class BasicAuthProvider<USERID, ROLE>(
    private val headerVal: Option<String>,
    private val userRealm: UserRealm<USERID, ROLE>
) : SecurityProvider<USERID, ROLE> {

    private val base64Decoder = Base64.getDecoder()

    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USERID, ROLE>> =
        headerVal.map { baseAuth: String ->
            Try.of {
                parseHeader(baseAuth)
            }.getOrElseGet { e -> left(SecurityErrorType.MalformedCredentials(e.localizedMessage)) }
        }.getOrElse {
            Out.right(AnonymousSecurityContext())
        }

    private fun parseHeader(baseAuth: String): Out<SecurityError, SecurityCtx<USERID, ROLE>> =
        if (baseAuth.startsWith(basicAuthHeaderPrefix)) {
            val decodedAut = base64Decoder.decode(baseAuth.substring(basicAuthHeaderPrefix.length))
            val colonIndex = decodedAut.indexOf(':'.toByte())
            if (colonIndex > 0) {
                val login = decodedAut.sliceArray(0 until colonIndex).toString(Charset.forName("UTF-8"))
                val pass = decodedAut.sliceArray(colonIndex + 1 until decodedAut.size)
                    .toCharArray()
                userRealm.loginUser(login, pass).map { user ->
                    pass.fill(0.toChar()) // I know that cleaning password in such insecure protocol is useless
                    Out.right<SecurityError, SecurityCtx<USERID, ROLE>>(UserSecurityContext(user, userRealm))
                }.getOrElse {
                    left<SecurityError, SecurityCtx<USERID, ROLE>>(SecurityErrorType.WrongCredentials(login))
                }
            } else {
                left<SecurityError, SecurityCtx<USERID, ROLE>>(
                    SecurityErrorType.MalformedCredentials("no colon inside header: $baseAuth")
                )
            }
        } else {
            left<SecurityError, SecurityCtx<USERID, ROLE>>(
                SecurityErrorType.MalformedCredentials("no basic auth header: $baseAuth")
            )
        }

    class AnonymousSecurityContext<USERID, ROLE> : SecurityCtx<USERID, ROLE> {
        override fun getCurrentUser(): Out<SecurityError, USERID> =
            left(SecurityErrorType.UnknownUser)

        override fun hasRole(role: ROLE): Boolean = false
    }

    class UserSecurityContext<USERID, ROLE>(
        private val user: USERID,
        private val userRealm: UserRealm<USERID, ROLE>
    ) : SecurityCtx<USERID, ROLE> {
        override fun getCurrentUser(): Out<SecurityError, USERID> = Out.right(user)

        override fun hasRole(role: ROLE): Boolean =
            userRealm.hasRole(user, role)
    }

    companion object {
        const val basicAuthHeaderPrefix = "Basic "
    }
}

internal fun ByteArray.toCharArray() = String(this).toCharArray()
