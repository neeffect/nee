package pl.setblack.nee.effects.monitoring

import io.vavr.collection.Vector
import java.util.concurrent.atomic.AtomicReference

//not tested

data class SimpleBufferedLogger(private val buffer : Vector<LogMessage> = Vector.empty())
    : Logger<SimpleBufferedLogger> {

    override fun log(entry: TraceEntry, msg: String) =
        this.copy(buffer.append(LogMessage(entry, msg)))

}

data class  LogMessage(val traceEntry: TraceEntry,val msg:String)
