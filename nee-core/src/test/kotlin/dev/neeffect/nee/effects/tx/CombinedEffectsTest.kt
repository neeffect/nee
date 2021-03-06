package dev.neeffect.nee.effects.tx

import dev.neeffect.nee.Nee
import dev.neeffect.nee.andThen
import dev.neeffect.nee.effects.async.AsyncStack
import dev.neeffect.nee.effects.async.CleanAsyncStack
import dev.neeffect.nee.effects.security.SecuredRunEffect
import dev.neeffect.nee.effects.security.SecurityError
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.security.SecurityProvider
import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.test.getLeft
import dev.neeffect.nee.effects.utils.merge
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.vavr.collection.List

internal class CombinedEffectsTest : BehaviorSpec({
    Given("Combined effects for admin") {
        val dbEff = TxEffect<DBLike, CombinedProviders>()
            .handleError { e -> CombinedError.TxError(e) as CombinedError }
        val secEff = SecuredRunEffect<String, String, CombinedProviders>("admin")
            .handleError { e -> CombinedError.SecurityError(e) as CombinedError }
        val combined = secEff.andThen(dbEff)
        When("Called with admin role") {
            val simpleAction = Nee.with(combined, function1)
            val db = DBLike()
            db.appendAnswer("6")
            val dbProvider = DBLikeProvider(db)
            val secProvider = TrivialSecurityProvider("irreg", List.of("admin"))
            val env = CombinedProviders(secProvider, dbProvider)
            val result = simpleAction.perform(env)
            Then("result should be 6") {

                result.get().get() shouldBe 6
            }
        }

        When("Called with no roles") {
            val simpleAction = Nee.with(combined, function1)
            val db = DBLike()
            db.appendAnswer("6")
            val dbProvider = DBLikeProvider(db)
            val secProvider = TrivialSecurityProvider("marreq", List.empty<String>())
            val env = CombinedProviders(secProvider, dbProvider)
            val result = simpleAction.perform(env)
            Then("result should be Insufficient roles") {
                result.getLeft().merge().secError() shouldBe SecurityErrorType.MissingRole(List.of("admin"))
            }
        }
    }
}) {
    companion object {
        val function1 = { db: CombinedProviders ->
            val resource = db.getConnection().getResource()
            val result = resource.query("SELECT * FROM all1")
            result.map {
                Integer.parseInt(it)
            }
        }
    }
}

sealed class CombinedError : TxError, SecurityError {
    class TxError(val internal: dev.neeffect.nee.effects.tx.TxError) : CombinedError() {
        override fun txError(): TxErrorType = internal.txError()
        override fun secError(): SecurityErrorType = TODO("??? maybe nullability")
    }

    class SecurityError(val internal: dev.neeffect.nee.effects.security.SecurityError) : CombinedError() {
        override fun txError(): TxErrorType = TODO()
        override fun secError(): SecurityErrorType = internal.secError()
    }
}

internal class CombinedProviders(
    val secProvider: SecurityProvider<String, String>,
    val txProvider: TxProvider<DBLike, DBLikeProvider>,
    val asyncStack: AsyncStack<CombinedProviders> = CleanAsyncStack()
) : SecurityProvider<String, String> by secProvider,
    TxProvider<DBLike, CombinedProviders> {
    override fun getConnection(): TxConnection<DBLike> = txProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<DBLike>): CombinedProviders =
        CombinedProviders(secProvider, txProvider.setConnectionState(newState), asyncStack)
}
