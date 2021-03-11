package dev.neeffect.nee.scratchpad

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

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
