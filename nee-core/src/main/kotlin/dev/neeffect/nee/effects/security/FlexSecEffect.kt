package dev.neeffect.nee.effects.security

import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.env.FlexibleEnv
import dev.neeffect.nee.effects.env.ResourceId
import io.vavr.collection.List

/**
 * Security effect - flex version.
 */
class FlexSecEffect<USER, ROLE>(private val roles: List<ROLE>) :
    Effect<FlexibleEnv, SecurityError> {
    private val internal =
        SecuredRunEffect<USER, ROLE, FlexSecurityProvider<USER, ROLE>>(
            roles
        )

    override fun <A> wrap(f: (FlexibleEnv) -> A):
                (FlexibleEnv) -> Pair<Out<SecurityError, A>, FlexibleEnv> = { env: FlexibleEnv ->
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
        }.getOrElse(
            Pair(
                Out.left<SecurityError, A>(
                    SecurityErrorType.NoSecurityCtx
                ), env
            )
        )
    }
}

/**
 * Provider of flex sec.
 */
class FlexSecurityProvider<USER, ROLE>(private val env: FlexibleEnv) :
    FlexibleEnv by env,
    SecurityProvider<USER, ROLE> {
    @Suppress("UNCHECKED_CAST")
    override fun getSecurityContext(): Out<SecurityError, SecurityCtx<USER, ROLE>> =
        env.get(ResourceId(SecurityProvider::class)).map { it.getSecurityContext() }
            .getOrElse(Out.left<SecurityError, SecurityCtx<USER, ROLE>>(SecurityErrorType.NoSecurityCtx))
                as Out<SecurityError, SecurityCtx<USER, ROLE>>
}
