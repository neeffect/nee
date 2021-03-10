package dev.neeffect.nee.effects.monitoring

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class EntryType {
    object Begin : EntryType()

    data class End(val elapsedTime: Long) : EntryType()

    data class InternalError(val msg: String) : EntryType() {
        override fun toString(): String = "Error($msg)"
    }
}
