package pl.setblack.nee.scratchpad

import kotlinx.coroutines.*
import pl.setblack.nee.Effect
import pl.setblack.nee.NEE


//public fun <T,R,E> CoroutineScope.eff(
//        effect : Effect<R,E>,
//        block: suspend CoroutineScope.() -> T) : NEE<R, E, Unit, T> {
//
//}


fun main() {
    val deferredResult: Deferred<String> = GlobalScope.async {
        val x = delay(1000L)
        "World!"
    }
    println("now I am here")
    runBlocking {
        println("Hello, ${deferredResult.await()}")
    }
}