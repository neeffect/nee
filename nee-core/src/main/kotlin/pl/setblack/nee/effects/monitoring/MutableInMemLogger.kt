package pl.setblack.nee.effects.monitoring

import io.vavr.collection.Seq
import java.util.concurrent.atomic.AtomicReference

class MutableInMemLogger : Logger<MutableInMemLogger> {
    private val internal = AtomicReference(SimpleBufferedLogger())

    override fun log(entry: LogEntry): MutableInMemLogger =
        internal.updateAndGet{it.log(entry)}.let { this }

    fun getLogs() : Seq<LogMessage> = internal.get().getLogs()
}
