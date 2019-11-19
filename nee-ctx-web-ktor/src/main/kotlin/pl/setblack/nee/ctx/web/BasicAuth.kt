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
import pl.setblack.nee.security.User
import pl.setblack.nee.security.UserRealm
import pl.setblack.nee.security.UserRole
import java.util.*

class BasicAuth<USER, ROLE>(private val userRealm: UserRealm<USER, ROLE>) {
    fun createSecurityProviderFromRequest(request: ApplicationRequest)
            : SecurityProvider<USER, ROLE> = BasicAuthProvider<USER, ROLE>(
        request.header("Authorization").option(), userRealm)


}


class BasicAuthProvider<USER, ROLE>(
    private val headerVal: Option<String>,
    private val userRealm: UserRealm<USER, ROLE>
) : SecurityProvider<USER, ROLE> {
    private val base64Decoder = Base64.getDecoder()
    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USER, ROLE>> =
        headerVal.map { baseAuth: String ->
            val decodedAut = base64Decoder.decode(baseAuth)
            val colonIndex = decodedAut.indexOf(':'.toByte())
            if (colonIndex > 0) {
                val login = decodedAut.sliceArray(0..colonIndex).toString()
                val pass = decodedAut.sliceArray(colonIndex..decodedAut.size)
                    .toCharArray()
                userRealm.loginUser(login, pass).map {user ->
                    Out.right<SecurityError, SecurityCtx<USER, ROLE>>(UserSecurityContext(user))
                }.getOrElse {
                    Out.left<SecurityError, SecurityCtx<USER, ROLE>>(SecurityErrorType.WrongCredentials(login))
                }
            } else {
                Out.left<SecurityError, SecurityCtx<USER, ROLE>>(SecurityErrorType.NoSecurityCtx)
            }
        }.getOrElse {
            Out.right(AnonymousSecurityContext())
        }

    class AnonymousSecurityContext<USER, ROLE> : SecurityCtx<USER, ROLE> {
        override fun getCurrentUser(): Out<SecurityError, USER> =
            left(SecurityErrorType.UnknownUser)

        override fun hasRole(role: ROLE): Boolean = false
    }

    class UserSecurityContext<USER, ROLE>(val user : USER) : SecurityCtx<USER, ROLE> {
        override fun getCurrentUser(): Out<SecurityError, USER> = Out.right(user)

        override fun hasRole(role: ROLE): Boolean {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }
}


internal fun ByteArray.toCharArray() = CharArray(this.size).also {
    chars ->
    for (index in 0 .. this.size) {
        chars[index] = this[index].toChar()
    }
}