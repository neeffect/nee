package dev.neeffect.nee.effects.security

import dev.neeffect.nee.effects.Out

class DummySecurityProvider<USER, ROLE> : SecurityProvider<USER, ROLE> {
    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USER, ROLE>> =
        Out.left(SecurityErrorType.NoSecurityCtx)
}
