package dev.neeffect.nee.ctx.web.jwt

import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.security.SecurityCtx
import dev.neeffect.nee.effects.security.SecurityError
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.effects.utils.merge
import dev.neeffect.nee.security.jwt.JwtConfigurationModule
import dev.neeffect.nee.security.jwt.JwtUsersCoder
import io.vavr.control.Option

class JwtAuthProvider<USER, ROLE>(
    private val headerVal: Option<String>,
    private val jwtConf: JwtConfigurationModule<USER, ROLE>
) : SecurityProvider<USER, ROLE> {
    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USER, ROLE>> =
        headerVal.map { fullHeader ->
            if (fullHeader.startsWith(bearerAuthHeaderPrefix)) {
                val jwtToken = fullHeader.substring(bearerAuthHeaderPrefix.length)
                jwtConf.jwtCoder.decodeJwt(jwtToken).map { jwt ->
                    jwtConf.jwtUsersCoder.decodeUser(jwt).map { user ->
                        Out.right<SecurityError, SecurityCtx<USER, ROLE>>(
                            TokenSecurityContext(user, jwtConf.jwtUsersCoder)
                        )
                    }.getOrElse {
                        Out.left(SecurityErrorType.MalformedCredentials("user not decoded from $jwt"))
                    }
                }.mapLeft { jwtError ->
                    Out.left<SecurityError, SecurityCtx<USER, ROLE>>(
                        SecurityErrorType.MalformedCredentials(jwtError.toString())
                    )
                }.merge()
            } else {
                Out.left<SecurityError, SecurityCtx<USER, ROLE>>(
                    SecurityErrorType.MalformedCredentials("wrong header $fullHeader")
                )
            }
        }.getOrElse {
            Out.left<SecurityError, SecurityCtx<USER, ROLE>>(SecurityErrorType.NoSecurityCtx)
        }

    companion object {
        const val bearerAuthHeaderPrefix = "Bearer "
    }
}

class TokenSecurityContext<USER, ROLE>(val user: USER, val jwtCoder: JwtUsersCoder<USER, ROLE>) :
    SecurityCtx<USER, ROLE> {
    override fun getCurrentUser(): Out<SecurityError, USER> =
        Out.right(user)

    override fun hasRole(role: ROLE): Boolean = jwtCoder.hasRole(user, role)
}
