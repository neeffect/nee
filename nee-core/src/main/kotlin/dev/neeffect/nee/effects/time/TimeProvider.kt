package dev.neeffect.nee.effects.time

import io.haste.Haste
import io.haste.TimeSource

interface TimeProvider {
    fun getTimeSource(): TimeSource
}

class HasteTimeProvider(private val timeSource: TimeSource = Haste.TimeSource.systemTimeSource()) : TimeProvider {
    override fun getTimeSource(): TimeSource = timeSource
}
