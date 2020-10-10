package pl.setblack.nee

import java.util.*

fun Pair<Long, Long>.toUUID() = UUID(this.first, this.second)
