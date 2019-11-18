package pl.setblack.nee.effects.cache.caffeine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

import pl.setblack.nee.effects.cache.CacheProvider
import java.util.concurrent.TimeUnit


class CaffeineProvider(private val cache : Cache<Any, Any>  = defaultCache())  : CacheProvider {

    override fun <K, V> computeIfAbsent(key: K, func: (K) -> V): V  =
        cache.get(key as Any, func as (Any)->Any) as V

    companion object {
        fun defaultCache() = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build<Any, Any>()
    }
}