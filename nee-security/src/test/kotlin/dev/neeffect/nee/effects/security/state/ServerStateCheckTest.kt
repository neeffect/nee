package dev.neeffect.nee.effects.security.state

import dev.neeffect.nee.security.state.ServerVerifier
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.*

internal class ServerStateCheckTest : DescribeSpec({
    describe("server state") {
        val serverState = ServerVerifier(Random(44L))
        val signedState = serverState.generateRandomSignedState()
        it("should verify signed state") {
            serverState.verifySignedText(signedState) shouldBe true
        }

        it("should fail verification of empty text") {
            serverState.verifySignedText("") shouldBe false
        }

        it("should fail verification of produced text") {
            serverState.verifySignedText("qGMeurZQOqugTVOzHvXA2g==@mJ2Y79oAAG3vifad3R/dFih749AjoWLx/EQ9lWa97RKXAstpXu2lMo9jGLAV8zN2eyY1tVvJ3RMdco/rngw9Cg+7sFp4QkSmAOHGXsQKiWs/nig1TkvpviNLNjJliE/WrmI7IPCL6x39hpgvvpGA/oo+iSyJdNct7CvLctqoetOs=") shouldBe false
        }
    }
})
