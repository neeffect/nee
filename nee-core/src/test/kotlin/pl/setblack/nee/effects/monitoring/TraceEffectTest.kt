package pl.setblack.nee.effects.monitoring

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.vavr.collection.List
import pl.setblack.nee.Nee
import pl.setblack.nee.effects.get
import pl.setblack.nee.effects.utils.ignoreR
import java.util.concurrent.atomic.AtomicLong

internal class TraceEffectTest : BehaviorSpec({
    Given("Trace") {
        val eff  = TraceEffect<SimpleTraceProvider>("tracerA")

        When("simple function process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = Nee.Companion.pure(eff, ignoreR(::plainFunction))
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
            }
        }
        When("monitored function process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = Nee.Companion.pure(eff, ::traceableFunction)
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
            }
        }
        When("simple function in obj process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = Nee.Companion.pure(eff,
                ignoreR(SomeObject::plainFunction)
            )
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
            }
        }
        When("monitored function in obj process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = Nee.Companion.pure(eff, SomeObject::traceableFunction)
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
            }
        }
    }
}) {
    companion object {

    }

    data class LogEntry(val trace : TraceEntry, val msg : String)

    class StoringLogger(internal var entries:List<LogEntry> = List.empty()) : Logger<StoringLogger> {
        override fun log(entry: TraceEntry, msg: String): StoringLogger  {
            entries = entries.append(LogEntry(entry, msg))
            return this
        }
    }
}


fun plainFunction(i : Int) = i+1

fun <R :TraceProvider<R>> traceableFunction(mon: R) = mon.getTrace().monitor().let { _ -> ::plainFunction}

object SomeObject {
    fun plainFunction(i : Int) = i+1

    fun <R :TraceProvider<R>> traceableFunction(mon: R) = mon.getTrace().monitor().let { _ -> ::plainFunction}
}
