package pl.setblack.nee.effects.security

import io.vavr.collection.List
import pl.setblack.nee.Effect
import pl.setblack.nee.effects.Out
import pl.setblack.nee.effects.env.FlexibleEnv
import pl.setblack.nee.effects.env.ResourceId

class FlexSecEffect<USER, ROLE>(private val roles: List<ROLE>) :
    Effect<FlexibleEnv, SecurityError> {
    private val internal =
        SecuredRunEffect<USER, ROLE, FlexSecurityProvider<USER, ROLE>>(
            roles
        )
    override fun <A, P> wrap(f: (FlexibleEnv) -> (P) -> A):
                (FlexibleEnv) -> Pair<(P) -> Out<SecurityError, A>, FlexibleEnv> = { env: FlexibleEnv ->
        val secProviderChance = env.get(ResourceId(SecurityProvider::class))
        secProviderChance.map { _ ->
            val flexSecProvider =
                FlexSecurityProvider<USER, ROLE>(env)
            val internalF = { _: SecurityProvider<USER, ROLE> ->
                f(env)
            }
            val wrapped = internal.wrap(internalF)
            val result = wrapped(flexSecProvider)
            Pair(result.first, env.set(ResourceId(SecurityProvider::class), result.second))
        }.getOrElse(Pair({ _: P ->
            Out.left<SecurityError, A>(
                SecurityErrorType.NoSecurityCtx
            )
        }, env))
    }
}

class FlexSecurityProvider<USER, ROLE>(private val env: FlexibleEnv) :
    FlexibleEnv by env,
    SecurityProvider<USER, ROLE> {
    @Suppress("UNCHECKED_CAST")
    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USER, ROLE>> =
        env.get(ResourceId(SecurityProvider::class)).map { it.getSecurityContext() }
            .getOrElse(Out.left<SecurityError, SecurityCtx<USER, ROLE>>(SecurityErrorType.NoSecurityCtx))
                as Out<SecurityError, SecurityCtx<USER, ROLE>>
}
