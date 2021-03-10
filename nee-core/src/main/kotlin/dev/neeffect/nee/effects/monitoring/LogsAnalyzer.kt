package dev.neeffect.nee.effects.monitoring

import io.vavr.collection.HashMap
import io.vavr.collection.List
import io.vavr.collection.Map
import io.vavr.collection.Seq
import io.vavr.collection.Stream
import java.util.UUID
import kotlin.collections.fold

class LogsAnalyzer {
    fun processLogs(logs: Seq<LogMessage>): LogsReport = logs.foldLeft(InvocationAccumulator()) { a, b ->
        a.addLog(b.traceEntry)
    }.group()
}

data class LogsReport(val classes: Seq<ClassReport> = List.empty())

data class ClassReport(val className: String, val functions: Stream<FunctionReport>)

data class FunctionReport(
    val codeLocation: CodeLocation,
    val invocationCount: Long,
    val totalTime: Long,
    val errorsCount: Long
) {
    operator fun plus(other: FunctionReport) =
        FunctionReport(
            this.codeLocation,
            this.invocationCount + other.invocationCount,
            this.totalTime + other.totalTime,
            this.errorsCount + other.errorsCount
        )
}

data class InvocationAccumulator(
    val starts: Map<UUID, LogEntry> = HashMap.empty(),
    val reports: HashMap<CodeLocation, FunctionReport> = HashMap.empty()
) {

    fun addLog(logEntry: LogEntry): InvocationAccumulator =
        when (logEntry.message) {
            is EntryType.Begin -> this.copy(starts = starts.put(logEntry.uuid, logEntry))
            is EntryType.End -> markEnd(logEntry)
            is EntryType.InternalError -> addError(logEntry)
        }

    private fun addError(logEntry: LogEntry) =
        FunctionReport(logEntry.codeLocation, 0, 0, 1).let { errorInv ->
            this.copy(reports = addReports(this.reports, errorInv))
        }

    private fun markEnd(logEntry: LogEntry): InvocationAccumulator =
        starts.get(logEntry.uuid).map { existingEntry ->
            addInvocation(existingEntry, logEntry)
        }.getOrElse {
            addError(logEntry)
        }

    private fun addInvocation(existingEntry: LogEntry, logEntry: LogEntry): InvocationAccumulator =
        makeReport(existingEntry, logEntry).let { invocationReport ->
            addReports(this.reports, invocationReport)
        }.let { reports ->
            this.copy(starts = starts.remove(existingEntry.uuid), reports = reports)
        }

    private fun addReports(reports: HashMap<CodeLocation, FunctionReport>, rep: FunctionReport) =
        reports.put(rep.codeLocation, rep) { prev, newVal ->
            prev + newVal
        }

    private fun makeReport(existingEntry: LogEntry, logEntry: LogEntry): FunctionReport =
        FunctionReport(logEntry.codeLocation, 1, logEntry.time - existingEntry.time, 0)

    fun group(): LogsReport = this.reports.values().groupBy {
        it.codeLocation.className
    }.iterator().fold(LogsReport()) { rep, b ->
        rep.copy(classes = rep.classes.append(ClassReport(b._1 ?: "???", b._2)))
    }
}
