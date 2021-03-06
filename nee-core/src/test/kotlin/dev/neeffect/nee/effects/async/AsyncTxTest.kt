package dev.neeffect.nee.effects.async

import dev.neeffect.nee.Nee
import dev.neeffect.nee.andThen
import dev.neeffect.nee.anyError
import dev.neeffect.nee.effects.test.get
import dev.neeffect.nee.effects.test.getAny
import dev.neeffect.nee.effects.tx.DBLike
import dev.neeffect.nee.effects.tx.DBLikeProvider
import dev.neeffect.nee.effects.tx.TxConnection
import dev.neeffect.nee.effects.tx.TxEffect
import dev.neeffect.nee.effects.tx.TxProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vavr.control.Either

internal class AsyncTxTest : DescribeSpec({
    describe("combined effect") {

        val action = Nee.Companion.with(combinedEffect) { env ->
            val connection = env.getConnection()
            if (connection.hasTransaction()) {
                "is trx"
            } else {
                "no trx"
            }
        }
        it("works in tx normally") {
            val db = DBLike()
            val initialEnv = AsyncEnv(DBLikeProvider(db), ecProvider)
            val result = action.perform(initialEnv)
            controllableExecutionContext.runSingle()
            val r1 = result.get()
            r1 shouldBe "is trx"
        }
        val nestedF = { prevResult: String ->
            Nee.with(combinedEffect) { env: AsyncEnv ->
                val connection = env.getConnection()
                val res = connection.getResource()
                if (connection.hasTransaction()) {
                    "$prevResult+is trx"
                } else {
                    "$prevResult+no trx"
                }
            }
        }

        val nestedAction = action.flatMap(nestedF)
        it("works in nested tx") {
            val db = DBLike()
            val initialEnv = AsyncEnv(DBLikeProvider(db), ecProvider)
            val result = nestedAction.perform(initialEnv)
            controllableExecutionContext.runSingle()
            controllableExecutionContext.runSingle()
            val r1 = result.getAny()
            r1 shouldBe Either.right<Any, String>("is trx+is trx")
        }
        it("works in double nested tx") {
            val dblNested = nestedAction.flatMap(nestedF)
            val db = DBLike()
            val initialEnv = AsyncEnv(DBLikeProvider(db), ecProvider)
            val result = dblNested.perform(initialEnv)
            controllableExecutionContext.runSingle()
            controllableExecutionContext.runSingle()
            controllableExecutionContext.runSingle()

            controllableExecutionContext.assertEmpty()

            val r1 = result.getAny()
            assertSoftly {
                r1 shouldBe Either.right<Any, String>("is trx+is trx+is trx")
                db.transactionLevel() shouldBe 0
            }
        }
    }
}) {
    internal companion object {

        val dbLikeEffect = TxEffect<DBLike, AsyncEnv>()
        val controllableExecutionContext = ControllableExecutionContext()
        val ecProvider = ECProvider(controllableExecutionContext)
        val asyncEffect = AsyncEffect<AsyncEnv>()
        val combinedEffect = asyncEffect.andThen(dbLikeEffect).anyError()

    }
}

internal data class AsyncEnv(
    val db: DBLikeProvider,
    val ex: ExecutionContextProvider,
    val asyncEnv: AsyncEnvWrapper<AsyncEnv> = AsyncEnvWrapper()
) :
    TxProvider<DBLike, AsyncEnv>, ExecutionContextProvider by ex, AsyncSupport<AsyncEnv> by asyncEnv {

    override fun getConnection(): TxConnection<DBLike> = this.db.getConnection()

    override fun setConnectionState(newState: TxConnection<DBLike>) = this.copy(db = db.setConnectionState(newState))

}
