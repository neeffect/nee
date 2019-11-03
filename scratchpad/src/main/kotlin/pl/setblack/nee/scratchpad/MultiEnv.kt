package pl.setblack.nee.scratchpad

import io.vavr.collection.HashMap
import io.vavr.collection.Map

data class MultiEnv(
    val allEnvs : Map<Any, Any> = HashMap.empty()
) {


}