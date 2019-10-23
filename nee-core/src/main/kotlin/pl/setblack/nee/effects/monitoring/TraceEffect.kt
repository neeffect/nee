package pl.setblack.nee.effects.monitoring

import java.util.*
import io.vavr.collection.List

class TraceEffect {

}


class TraceResource(
    val tracerName: String,
    val logger: Logger,
    val traces: List<TraceEntry> = List.empty<TraceEntry>()
) {


    internal inline fun begin(): TraceResource =
        TraceEntry(generateUUID()).let { traceEntry ->

            TraceResource(
                this.tracerName,
                logger.log(traceEntry),
                traces.prepend(traceEntry)
            )
        }


    private fun generateUUID(): UUID = UUID.randomUUID()

    private fun lastTrace() = this.traces.headOption()

    internal inline fun end(): TraceResource = lastTrace().let {
        it.map { traceEntry ->
            TraceResource(
                this.tracerName,
                logger.log(traceEntry),
                traces.pop()
            )
        }.getOrElse {
            logger.log(TraceEntry(UUID(0,0), "unpaired end at: ${placeName()}" ))
            this
        }
    }


}

inline fun placeName(): String {
    val stackTrace = Thread.currentThread().stackTrace
    return if (stackTrace.size > 0) {
        val st = stackTrace[0]
        "${st.moduleName} ${st.fileName} ${st.lineNumber} ${st.methodName}"
    } else {
        "<unknown place>"
    }
}

interface Logger {
    fun log(entry: TraceEntry): Logger
}

data class TraceEntry(val uuid: UUID, val name: String? = null)