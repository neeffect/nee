package dev.neeffect.nee.effects.monitoring

import dev.neeffect.nee.toUUID
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vavr.collection.List

internal class LogsAnalyzerTest : DescribeSpec({
    describe("logsAnalyzer") {
        val logsAnalyzer = LogsAnalyzer()
        describe("simple report") {
            val simpleReport = logsAnalyzer.processLogs(simpleSeq.map(::LogMessage))
            it("registers single invocation ") {
                simpleReport.classes[0].functions[0].invocationCount shouldBe 1
            }

            it("calculates single invocation time") {
                simpleReport.classes[0].functions[0].totalTime shouldBe 100
            }
        }
        describe("report with 3 invocations") {
            val report = logsAnalyzer.processLogs(tripleSeq.map(::LogMessage))
            it("registers 3 invocation ") {
                report.classes[0].functions[0].invocationCount shouldBe 3
            }
            it("registers total time ") {
                report.classes[0].functions[0].totalTime shouldBe 300
            }
            it("has only one class") {
                report.classes.size() shouldBe 1
            }
        }
        describe("nested calls report") {
            val report = logsAnalyzer.processLogs(nestedSeq.map(::LogMessage))
            it("registers single invocation per each function ") {
                report.classes[0].functions[0].invocationCount shouldBe 1
            }

            it("finds two functions") {
                report.classes[0].functions.size() shouldBe 2
            }
        }
        describe("broken calls report (missing start)") {
            val report = logsAnalyzer.processLogs(brokenSeq.map(::LogMessage))
            it("registers single error ") {
                report.classes[0].functions[0].errorsCount shouldBe 1
            }
        }

        describe("error calls is reported") {
            val report = logsAnalyzer.processLogs(errorSeq.map(::LogMessage))
            it("registers single error ") {
                report.classes[0].functions[0].errorsCount shouldBe 1
            }
        }


    }
}) {
    companion object {
        val a1CodeLocation = CodeLocation(functionName = "a1", className = "c1")
        val b1CodeLocation = CodeLocation(functionName = "b1", className = "c1")

        val a1Entry = LogEntry("x", Pair(0L, 1L).toUUID(), 1, a1CodeLocation, EntryType.Begin)
        val a1EntryEnd = a1Entry.copy(time = a1Entry.time + 100, message = EntryType.End(100))
        val b1Entry = LogEntry("x", Pair(0L, 2L).toUUID(), 10, b1CodeLocation, EntryType.Begin)
        val b1EntryEnd = b1Entry.copy(time = b1Entry.time + 50, message = EntryType.End(40))

        val a1EntryError = a1Entry.copy(time = a1Entry.time + 100, message = EntryType.InternalError("test"))


        val simpleSeq = List.of(a1Entry, a1EntryEnd)
        val tripleSeq = simpleSeq.prependAll(simpleSeq).prependAll(simpleSeq)
        val nestedSeq = List.of(a1Entry, b1Entry, b1EntryEnd, a1EntryEnd)

        val brokenSeq = List.of(a1Entry, b1EntryEnd, a1EntryEnd)

        val errorSeq = List.of(a1Entry, a1EntryError, a1EntryEnd)

    }
}
