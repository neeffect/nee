package dev.neeffect.nee.effects.cache

import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.Out
import java.util.concurrent.ConcurrentHashMap

class CacheEffect<R, E, P>(
    private val p: P,
    private val cacheProvider: CacheProvider
) : Effect<R, E> {
    override fun <A> wrap(f: (R) -> A): (R) -> Pair<Out<E, A>, R> = { r: R ->
        Pair(
            Out.right<E, A>(cacheProvider.computeIfAbsent(p, { f(r) })), r
        )
    }
}

interface CacheProvider {
    fun <K, V> computeIfAbsent(key: K, func: (K) -> V): V
}

@Suppress("MutableCollections")
class NaiveCacheProvider : CacheProvider {
    private val map: ConcurrentHashMap<Any, Any> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    override fun <K, V> computeIfAbsent(key: K, func: (K) -> V): V =
        map.computeIfAbsent(key as Any) { k: Any -> func(k as K) as Any } as V
}
