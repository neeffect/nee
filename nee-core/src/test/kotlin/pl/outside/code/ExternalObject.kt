package pl.outside.code

import pl.setblack.nee.Nee
import pl.setblack.nee.NoEffect
import pl.setblack.nee.effects.monitoring.CodeNameFinder.guessCodePlaceName
import pl.setblack.nee.effects.monitoring.TraceProvider

object ExternalObject {
    fun plainFunction(i : Int) = i+1

    fun <R : TraceProvider<R>> traceableFunction(mon: R) =
        mon.getTrace().putNamedPlace(guessCodePlaceName(2)).let { _ -> ::plainFunction}

    fun checkWhereCodeIsSimple(a: Unit) = guessCodePlaceName()

    fun checkWhereCodeIsNee() = Nee.Companion.pure(NoEffect<Unit,Unit>()){ {_:Unit ->
        guessCodePlaceName()

    } }
}
