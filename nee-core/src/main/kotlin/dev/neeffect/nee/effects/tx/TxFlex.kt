package dev.neeffect.nee.effects.tx

import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.env.FlexibleEnv
import dev.neeffect.nee.effects.env.ResourceId
import dev.neeffect.nee.effects.env.with
import dev.neeffect.nee.effects.tx.FlexTxProvider.Companion.txProviderResource
import io.vavr.control.Option

/**
 * Transaction (flexible env version).
 */
class FlexTxEffect<R> : Effect<FlexibleEnv, TxError> {
    private val internal = TxEffect<R, FlexTxProvider<R>>()

    override fun <A> wrap(f: (FlexibleEnv) -> A): (FlexibleEnv) -> Pair<Out<TxError, A>, FlexibleEnv> =
        { env: FlexibleEnv ->
            @Suppress("UNCHECKED_CAST")
            val providerChance = env.get(txProviderResource)
                    as Option<TxProvider<R, *>>
            providerChance.map { _ ->
                val flexProvider = FlexTxProvider<R>(env)
                val internalF = { _: TxProvider<R, *> ->
                    f(env)
                }
                val wrapped = internal.wrap(internalF)
                val result = wrapped(flexProvider)
                Pair(result.first, result.second.env)
            }.getOrElse(Pair(Out.left<TxError, A>(TxErrorType.NoConnection), env))
        }
}

internal class FlexTxProvider<R>(internal val env: FlexibleEnv) :
    TxProvider<R, FlexTxProvider<R>> {
    override fun getConnection(): TxConnection<R> =
        env.get(txProviderResource).map {
            @Suppress("UNCHECKED_CAST")
            it.getConnection() as TxConnection<R>
        }.getOrElseThrow {
            IllegalStateException("no connection for tx")
        }

    @Suppress("UNCHECKED_CAST")
    override fun setConnectionState(newState: TxConnection<R>): FlexTxProvider<R> = env.get(txProviderResource)
        .map { provider ->
            val p = provider as TxProvider<R, *>
            val newProvider = p.setConnectionState(newState) as TxProvider<R, *>
            val newEnv = env.set(txProviderResource, newProvider)
            FlexTxProvider<R>(newEnv)
        }.getOrElseThrow {
            IllegalStateException("no connection provider")
        }

    companion object {
        val txProviderResource = ResourceId(TxProvider::class)

        @Suppress("UNCHECKED_CAST")
        fun <R> connection(env: FlexibleEnv): R =
            env.get(txProviderResource).map {
                it.getConnection() as TxConnection<R>
            }.map {
                it.getResource()
            }.getOrElseThrow { java.lang.IllegalStateException("Connection provider  must be available") }
    }
}

fun <R, G : TxProvider<R, G>> FlexibleEnv.withTxProvider(provider: TxProvider<R, G>) =
    this.with(txProviderResource, provider)

@Suppress("UNCHECKED_CAST")
inline fun <reified T : FlexibleEnv, A> ((T) -> A).flex(): (FlexibleEnv) -> A =
    this as (FlexibleEnv) -> A
