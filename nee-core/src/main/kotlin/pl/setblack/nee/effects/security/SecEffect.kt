package pl.setblack.nee.effects.security

import io.vavr.collection.List
import pl.setblack.nee.Effect
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.env.FlexibleEnv
import pl.setblack.nee.effects.env.ResourceId

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

sealed class SecurityErrorType : SecurityError {
    override fun secError() = this
    class WrongCredentials(val message : String = "") : SecurityErrorType()
    object UnknownUser : SecurityErrorType()
    object NoSecurityCtx : SecurityErrorType()
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


class FlexSecEffect<USER, ROLE>(private val roles: List<ROLE>) : Effect<FlexibleEnv, SecurityError> {
    private val internal = SecuredRunEffect<USER, ROLE, FlexSecurityProvider<USER, ROLE>>(roles)
    override fun <A, P> wrap(f: (FlexibleEnv) -> (P) -> A): (FlexibleEnv) -> Pair<(P) -> Out<SecurityError, A>, FlexibleEnv> =
        { env: FlexibleEnv ->
            val secProviderChance = env.get(ResourceId(SecurityProvider::class))
            secProviderChance.map { secProvider ->
                val flexSecProvider = FlexSecurityProvider<USER, ROLE>(env)
                val internalF = { secProviderInternal: SecurityProvider<USER, ROLE> ->
                    f(env)
                }
                val wrapped = internal.wrap(internalF)
                val result = wrapped(flexSecProvider)
                Pair(result.first, env.set(result.second, ResourceId(SecurityProvider::class)))
            }.getOrElse(Pair({ _: P -> Out.left<SecurityError, A>(SecurityErrorType.NoSecurityCtx) }, env))
        }
}


class FlexSecurityProvider<USER, ROLE>(private val env: FlexibleEnv) :
    FlexibleEnv by env,
    SecurityProvider<USER, ROLE> {
    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USER, ROLE>> =
        env.get(ResourceId(SecurityProvider::class)).map { it.getSecurityContext() }
            .getOrElse(Out.left<SecurityError, SecurityCtx<USER, ROLE>>(SecurityErrorType.NoSecurityCtx)) as Out<SecurityError, SecurityCtx<USER, ROLE>>

}



