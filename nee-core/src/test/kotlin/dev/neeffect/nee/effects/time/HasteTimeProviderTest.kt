package dev.neeffect.nee.effects.time

import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * trivial test of library
 * (does not work unless haste is fixed)
 */
internal class HasteTimeProviderTest : DescribeSpec({
    describe("haste timeSource") {
        val haste = Haste.TimeSource.withFixedClock(
            Clock.fixed(Instant.parse("2020-10-24T22:22:03.00Z"), ZoneId.of("Europe/Berlin"))
        )
        val provider = HasteTimeProvider(haste)
        it("gives back fixed time") {
            provider.getTimeSource().now().toString() shouldBe "2020-10-25T00:22:03+02:00[Europe/Berlin]"
        }
        it("gives back moved time") {
            haste.advanceTimeBy(6, TimeUnit.HOURS)
            provider.getTimeSource().now().toString() shouldBe "2020-10-25T05:22:03+01:00[Europe/Berlin]"
        }
    }
})
