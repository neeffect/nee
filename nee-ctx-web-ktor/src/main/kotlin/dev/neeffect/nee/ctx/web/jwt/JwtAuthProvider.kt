package dev.neeffect.nee.ctx.web.jwt

import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.security.SecurityCtx
import dev.neeffect.nee.effects.security.SecurityError
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.security.UserRealm
import io.vavr.control.Option

class JwtAuthProvider<USERID, ROLE>(
    private val headerVal: Option<String>
) : SecurityProvider<USERID, ROLE> {
    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USERID, ROLE>> {
        TODO("Not yet implemented")
    }

}
