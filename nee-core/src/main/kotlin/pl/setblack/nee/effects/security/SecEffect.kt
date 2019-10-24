package pl.setblack.nee.effects.security

import io.vavr.collection.List
import io.vavr.control.Either
import pl.setblack.nee.Effect
import pl.setblack.nee.effects.Fe

interface SecurityCtx<USER, ROLE> {
    fun getCurrentUser(): USER
    fun hasRole(role: ROLE): Boolean
}

interface SecurityProvider<USER, ROLE> {
    fun getSecurityContext(): Fe<SecurityError, SecurityCtx<USER, ROLE>>
}

interface SecurityError {
    fun secError(): SecurityErrorType
}

sealed class SecurityErrorType : SecurityError {
    override fun secError() = this

    object UnknownUser : SecurityErrorType()
    object NoSecurityCtx : SecurityErrorType()
    data class MissingRole<ROLE>(val roles: List<ROLE>) : SecurityErrorType()
}

class SecuredRunEffect<USER, ROLE, R : SecurityProvider<USER, ROLE>>(
    private val roles: List<ROLE>
) : Effect<R,
        SecurityError> {

    constructor(singleRole: ROLE) : this(List.of(singleRole))

    override fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Fe<SecurityError, A>, R> {
        return { provider: R ->
                Pair( //TODO - fail faster
                    { p: P ->provider.getSecurityContext() .flatMap { securityCtx ->
                    val missingRoles = roles.filter {role ->
                        !securityCtx.hasRole(role)
                    }
                    if ( missingRoles.isEmpty) {
                        Fe.right(f(provider)(p))
                    } else {
                        Fe.left<SecurityError, A>(SecurityErrorType.MissingRole(missingRoles))
                    }
                }}, provider)
        }
    }
}

