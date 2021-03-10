package dev.neeffect.nee.effects.monitoring

import io.vavr.collection.Seq
import java.util.concurrent.atomic.AtomicReference

class MutableInMemLogger : Logger<MutableInMemLogger>, LogsProvider {
    private val logsAnalyzer = LogsAnalyzer()

    private val internal = AtomicReference(SimpleBufferedLogger())

    override fun log(entry: LogEntry): MutableInMemLogger =
        internal.updateAndGet { it.log(entry) }.let { this }

    override fun getLogs(): Seq<LogMessage> = internal.get().getLogs()

    override fun getReport(): LogsReport = logsAnalyzer.processLogs(internal.get().getLogs())
}
