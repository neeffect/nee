package dev.neeffect.nee

import java.util.*

fun Pair<Long, Long>.toUUID() = UUID(this.first, this.second)
