package pl.setblack.nee.effects.security

import io.vavr.collection.List
import pl.setblack.nee.Effect
import pl.setblack.nee.effects.Out

interface SecurityCtx<USER, ROLE> {
    fun getCurrentUser(): Out<SecurityError, USER>
    fun hasRole(role: ROLE): Boolean
}

interface SecurityProvider<USER, ROLE> {
    fun getSecurityContext(): Out<SecurityError, SecurityCtx<USER, ROLE>>
}

interface SecurityError {
    fun secError(): SecurityErrorType
}

/**
 * Error on security check.
 */
sealed class SecurityErrorType : SecurityError {
    override fun secError() = this

    /**
     * Credentials were wrong.
     */
    class WrongCredentials(val message: String = "") : SecurityErrorType()
    /**
     * User not recognized.
     */
    object UnknownUser : SecurityErrorType()

    /**
     * Security context nott available.
     */
    object NoSecurityCtx : SecurityErrorType()

    /**
     * Credential cannot be parsed.
     */
    data class MalformedCredentials(val message: String = "") : SecurityErrorType()

    /**
     * Expected role is missing.
     */
    data class MissingRole<ROLE>(val roles: List<ROLE>) : SecurityErrorType()
}

class SecuredRunEffect<USER, ROLE, R : SecurityProvider<USER, ROLE>>(
    private val roles: List<ROLE>
) : Effect<R,
        SecurityError> {

    constructor(singleRole: ROLE) : this(List.of(singleRole))

    override fun <A, P> wrap(f: (R) -> (P) -> A): (R) -> Pair<(P) -> Out<SecurityError, A>, R> {
        return { provider: R ->
            Pair( //TODO - fail faster?
                { p: P ->
                    provider.getSecurityContext().flatMap { securityCtx ->
                        val missingRoles = roles.filter { role ->
                            !securityCtx.hasRole(role)
                        }
                        if (missingRoles.isEmpty) {
                            Out.right(f(provider)(p))
                        } else {
                            Out.left<SecurityError, A>(SecurityErrorType.MissingRole(missingRoles))
                        }
                    }
                }, provider
            )
        }
    }
}
