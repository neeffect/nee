package pl.setblack.nee.effects.tx

import io.vavr.control.Either
import io.vavr.collection.List
import pl.setblack.nee.effects.security.*

fun scratchPad() {


}


class SimpleSecurityProvider<USER, ROLE>(user: USER, roles: List<ROLE>) : SecurityProvider<USER, ROLE> {
    private val ctx = SimpleSecurityContext(user, roles)
    override fun getSecurityContext(): Either<SecurityError, SecurityCtx<USER, ROLE>> = Either.right(ctx)

    internal class SimpleSecurityContext<USER, ROLE>(private val user: USER, private val roles: List<ROLE>) :
        SecurityCtx<USER, ROLE> {
        override fun getCurrentUser(): USER = user
        override fun hasRole(role: ROLE): Boolean = roles.contains(role)
    }
}

