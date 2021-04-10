package dev.neeffect.nee.effects.env

import io.vavr.control.Option
import io.vavr.control.Option.some
import kotlin.reflect.KClass

/**
 * Key for a resource to get.
 */
data class ResourceId<T : Any>(val clazz: KClass<T>, val key: Any = DefaultKey) {
    object DefaultKey
}

/**
 * Allows for runtime expandable Environment.
 *
 *  This neglects type safety of R, but might be in fact way easier to use.
 * TODO actually needed sealed class but it did not work
 */
interface FlexibleEnv {
    fun <T : Any> get(id: ResourceId<T>): Option<T>
    fun <T : Any> set(id: ResourceId<T>, t: T): FlexibleEnv

    companion object {
        inline fun <reified T : Any> create(
            id: ResourceId<T>,
            t: T
        ): FlexibleEnv =
            WrappedEnv(t, id, EnvLeaf)

        inline fun <reified T : Any> create(
            t: T
        ): FlexibleEnv = create(ResourceId(T::class), t)

        fun empty(): FlexibleEnv = EnvLeaf
    }
}

/**
 * Add next type to env.
 */
inline fun <reified T : Any> FlexibleEnv.with(
    t: T
): FlexibleEnv = with(ResourceId(T::class), t)

inline fun <reified T : Any> FlexibleEnv.with(
    id: ResourceId<T>,
    t: T
): FlexibleEnv = WrappedEnv(t, id, this)

@Suppress("ThrowExpression")
object EnvLeaf : FlexibleEnv {
    override fun <T : Any> get(id: ResourceId<T>): Option<T> = Option.none()

    override fun <T : Any> set(id: ResourceId<T>, t: T): FlexibleEnv =
        throw IllegalArgumentException("Impossible to set resource of type $id")
}

/**
 * Node instance of  flexibleEnv
 */
data class WrappedEnv<Y : Any>(
    private val env: Y,
    private val resId: ResourceId<Y>,
    private val inner: FlexibleEnv
) : FlexibleEnv {
    override fun <T : Any> get(id: ResourceId<T>): Option<T> =
        if (id == resId) {
            @Suppress("UNCHECKED_CAST")
            some(env) as Option<T>
        } else {
            inner.get(id)
        }

    override fun <T : Any> set(id: ResourceId<T>, t: T): FlexibleEnv =
        if (id == resId) {
            WrappedEnv(t, id, inner)
        } else {
            WrappedEnv(env, resId, inner.set(id, t))
        }
}
