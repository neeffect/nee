package pl.setblack.nee.effects.tx

import io.kotlintest.shouldBe
import io.kotlintest.specs.BehaviorSpec
import io.vavr.collection.List
import pl.setblack.nee.NEE
import pl.setblack.nee.effects.security.SecuredRunEffect
import pl.setblack.nee.effects.security.SecurityError
import pl.setblack.nee.effects.security.SecurityErrorType
import pl.setblack.nee.effects.security.SecurityProvider

internal class CombinedEffectsTest : BehaviorSpec({
    Given("Combined effects for admin") {
        val dbEff = TxEffect<DBLike, CombinedProviders>().handleError { e -> CombinedError.TxError(e) as CombinedError }
        val secEff = SecuredRunEffect<String, String, CombinedProviders>("admin")
            .handleError { e -> CombinedError.SecurityError(e) as CombinedError }
        val combined = secEff.andThen(dbEff)
        When("Called with admin role") {
            val simpleAction = NEE.pure(combined) { db: CombinedProviders ->
                val resource = db.getConnection().getResource()
                val result = resource.query("SELECT * FROM all1")
                result.map {
                    Integer.parseInt(it)
                }
                    .toEither<CombinedError>(CombinedError.TxError(TxErrorType.CannotQuery("I do not know"))) //TODO this is so so
            }
            val db = DBLike()
            db.appendAnswer("6")
            val dbProvider = DBLikeProvider(db)
            val secProvider = SimpleSecurityProvider("irreg", List.of("admin"))
            val env = CombinedProviders(secProvider, dbProvider)
            val result = simpleAction.perform(env)
            Then("result should be 6") {
                result.get() shouldBe 6
            }
        }

        When("Called with no roles") {
            val simpleAction = NEE.pure(combined) { db: CombinedProviders ->
                val resource = db.getConnection().getResource()
                val result = resource.query("SELECT * FROM all1")
                result.map {
                    Integer.parseInt(it)
                }
                    .toEither<CombinedError>(CombinedError.TxError(TxErrorType.CannotQuery("I do not know"))) //TODO this is so so
            }
            val db = DBLike()
            db.appendAnswer("6")
            val dbProvider = DBLikeProvider(db)
            val secProvider = SimpleSecurityProvider("marreq", List.empty<String>())
            val env = CombinedProviders(secProvider, dbProvider)
            val result = simpleAction.perform(env)
            Then("result should be Insufficient roles") {
                result.left.secError() shouldBe SecurityErrorType.MissingRole(List.of("admin"))
            }
        }
    }


})

sealed class CombinedError : TxError, SecurityError {
    class TxError(val internal: pl.setblack.nee.effects.tx.TxError) : CombinedError() {
        override fun txError(): TxErrorType = internal.txError();
        override fun secError(): SecurityErrorType = TODO("???")
    }

    class SecurityError(val internal: pl.setblack.nee.effects.security.SecurityError) : CombinedError() {
        override fun txError(): TxErrorType = TODO()
        override fun secError(): SecurityErrorType = internal.secError()
    }
}

class CombinedProviders(
    val secProvider: SecurityProvider<String, String>,
    val txProvider: TxProvider<DBLike, DBLikeProvider>
) : SecurityProvider<String, String> by secProvider,
    TxProvider<DBLike, CombinedProviders> {
    override fun getConnection(): TxConnection<DBLike> = txProvider.getConnection()

    override fun setConnectionState(newState: TxConnection<DBLike>): CombinedProviders =
        CombinedProviders(secProvider, txProvider.setConnectionState(newState))
}