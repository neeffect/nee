package dev.neeffect.nee.effects.monitoring

class SimpleTraceProvider(val res: TraceResource) : TraceProvider<SimpleTraceProvider> {
    override fun getTrace(): TraceResource = res

    override fun setTrace(newState: TraceResource): SimpleTraceProvider = SimpleTraceProvider(newState)
}
