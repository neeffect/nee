package dev.neeffect.nee.effects.tx

import dev.neeffect.nee.Effect
import dev.neeffect.nee.effects.Out

data class TestResource(val version: Int)

class TestEffect(val name: String, val log: MutableList<String>) : Effect<TestResource, String> {
    override fun <A> wrap(f: (TestResource) -> A): (TestResource) -> Pair<Out<String, A>, TestResource> =
        { r: TestResource ->
            log("enter test effect. Res $r")
            Pair(
                run {
                    val newR = r.copy(version = r.version + 10)
                    log("calling test effect. Res $newR")
                    Out.right<String, A>(f(newR))
                }, r.copy(version = r.version + 100)
            )
        }

    fun log(msg: String) = log.add("$name: $msg")
}
