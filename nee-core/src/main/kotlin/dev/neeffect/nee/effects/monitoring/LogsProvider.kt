package dev.neeffect.nee.effects.monitoring

import io.vavr.collection.Seq

interface LogsProvider {
    fun getLogs(): Seq<LogMessage>

    fun getReport(): LogsReport
}
