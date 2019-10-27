package pl.setblack.nee.effects.monitoring

import io.kotlintest.specs.BehaviorSpec
import io.kotlintest.shouldBe
import io.vavr.collection.List
import pl.setblack.nee.NEE
import pl.setblack.nee.effects.get
import pl.setblack.nee.ignoreR
import java.util.concurrent.atomic.AtomicLong

class TraceEffectTest : BehaviorSpec({
    Given("Trace") {
        val eff  = TraceEffect<SimpleTraceProvider>("tracerA")

        When("simple function process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = NEE.Companion.pure(eff, ignoreR(::plainFunction))
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
                println(logger.entries)
            }
        }
        When("monitored function process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = NEE.Companion.pure(eff, ::traceableFunction)
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
                println(logger.entries)
            }
        }
        When("simple function in obj process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = NEE.Companion.pure(eff, ignoreR(SomeObject::plainFunction))
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
                println(logger.entries)
            }
        }
        When("monitored function in obj process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = NEE.Companion.pure(eff, SomeObject::traceableFunction)
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
                println(logger.entries)
            }
        }
    }
}) {
    companion object {

    }

    data class LogEntry(val trace : TraceEntry, val msg : String)

    class StoringLogger(internal var entries:List<LogEntry> = List.empty()) : Logger {
        override fun log(entry: TraceEntry, msg: String): Logger  {
            entries = entries.append(LogEntry(entry, msg))
            return this
        }
    }

    class SimpleTraceProvider(val res  : TraceResource) : TraceProvider<SimpleTraceProvider> {
        override fun getTrace(): TraceResource = res

        override fun setTrace(newState: TraceResource): SimpleTraceProvider  = SimpleTraceProvider(newState)

    }


}


fun plainFunction(i : Int) = i+1

fun <R :TraceProvider<R>> traceableFunction(mon: R) = mon.getTrace().monitor().let { _ -> ::plainFunction}

object SomeObject {
    fun plainFunction(i : Int) = i+1

    fun <R :TraceProvider<R>> traceableFunction(mon: R) = mon.getTrace().monitor().let { _ -> ::plainFunction}
}