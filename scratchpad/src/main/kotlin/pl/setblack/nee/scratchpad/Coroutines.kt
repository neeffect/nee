package pl.setblack.nee.scratchpad

import kotlinx.coroutines.*


//public fun <T,R,E> CoroutineScope.eff(
//        effect : Effect<R,E>,
//        block: suspend CoroutineScope.() -> T) : NEE<R, E, Unit, T> {
//
//}


fun main() {
    val deferredResult: Deferred<String> = GlobalScope.async {
        delay(1000L)
        "World!"
    }
    println("now I am here")
    runBlocking {
        println("Hello, ${deferredResult.await()}")
    }
}