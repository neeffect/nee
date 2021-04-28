package dev.neeffect.nee.effects.monitoring

import io.vavr.collection.Seq
import io.vavr.collection.Vector

data class SimpleBufferedLogger(private val buffer: Vector<LogMessage> = Vector.empty()) :
    Logger<SimpleBufferedLogger> {

    override fun log(entry: LogEntry) =
        this.copy(buffer.append(LogMessage(entry)))

    fun getLogs(): Seq<LogMessage> = buffer
}

data class LogMessage(val traceEntry: LogEntry)
