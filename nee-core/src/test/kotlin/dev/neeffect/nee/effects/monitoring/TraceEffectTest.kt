package dev.neeffect.nee.effects.monitoring

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.vavr.collection.List
import pl.outside.code.ExternalObject
import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.effects.get
import dev.neeffect.nee.effects.utils.ignoreR
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
        When ("function is processed 100ms") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f =
                Nee.Companion.pure(eff) { r ->
                    { p: Unit ->
                        time.updateAndGet { it + 100 * 1000 }
                    }
                }
            val result = f.perform(SimpleTraceProvider(res))(Unit)
            Then("time is measured"){
                logger.entries[1].time shouldBe ( 100L + 100000L)
            }
        }
        When("simple function in external code obj process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = Nee.Companion.pure(eff,
                ignoreR(ExternalObject::plainFunction)
            )
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
            }
            Then("logs contain correctly guessed name") {
                logger.entries[1].codeLocation.className shouldContain  "addWhen"
            }

        }
        When("monitored function in obj process") {
            val logger = StoringLogger()
            var time = AtomicLong(100)
            val res = TraceResource("z1", logger, {  time.get()})
            val f = Nee.Companion.pure(eff, ExternalObject::traceableFunction)
            val result = f.perform(SimpleTraceProvider(res))(5)
            Then("result is ok"){
                result.get() shouldBe 6
            }
            Then("logs contain correctly guessed name") {
                logger.entries[1].codeLocation.toString() shouldStartWith "pl.outside.code.ExternalObject->traceableFunction#ExternalObject.kt"
            }
        }
    }
    Given("guessing code name function") {
        When("called simple function in Nee") {
            val noEffect  = NoEffect<Unit, Unit>()
            val result = Nee.constR(noEffect, ExternalObject::checkWhereCodeIsSimple).perform(Unit)(Unit)
            Then("name of function recognized"){
                result.get().toString() shouldStartWith  "pl.outside.code.ExternalObject->checkWhereCodeIsSimple#ExternalObject.kt@"
            }
        }

        When("called wrapped function in Nee") {
            val result = ExternalObject.checkWhereCodeIsNee().perform(Unit)(Unit)
            Then("name of wrapped function recognized"){
                result.get().toString() shouldStartWith "pl.outside.code.ExternalObject\$checkWhereCodeIsNee\$"
            }
        }
    }
}) {
    companion object {

    }



    class StoringLogger(internal var entries:List<LogEntry> = List.empty()) : Logger<StoringLogger> {
        override fun log(entry: LogEntry): StoringLogger  {
            entries = entries.append(entry)
            return this
        }
    }
}


fun plainFunction(i : Int) = i+1

fun <R :TraceProvider<R>> traceableFunction(mon: R) = mon.getTrace().putNamedPlace().let { _ -> ::plainFunction}


