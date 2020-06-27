package pl.setblack.nee.effects.async

import io.kotlintest.shouldBe
import io.kotlintest.specs.DescribeSpec
import io.vavr.control.Either
import pl.setblack.nee.Nee
import pl.setblack.nee.andThen
import pl.setblack.nee.anyError
import pl.setblack.nee.effects.get
import pl.setblack.nee.effects.getAny
import pl.setblack.nee.effects.tx.DBLike
import pl.setblack.nee.effects.tx.DBLikeProvider
import pl.setblack.nee.effects.tx.TxConnection
import pl.setblack.nee.effects.tx.TxEffect
import pl.setblack.nee.effects.tx.TxProvider

class AsyncTxTest : DescribeSpec({
    describe("combined effect") {

        val action = Nee.Companion.constP(combinedEffect) { env ->
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
            val result = action.perform(initialEnv)(Unit)
            controllableExecutionContext.runSingle()
            val r1 = result.get()
            r1 shouldBe "is trx"
        }
        val nestedF = { prevResult: String ->
            Nee.constP(combinedEffect) { env: AsyncEnv ->
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
            val result = nestedAction.perform(initialEnv)(Unit)
            controllableExecutionContext.runSingle()
            controllableExecutionContext.runSingle()
            val r1 = result.getAny()
            r1 shouldBe Either.right<Any, String>("is trx+is trx")
        }
        it("works in double nested tx") {
            val dblNested = nestedAction.flatMap(nestedF)
            val db = DBLike()
            val initialEnv = AsyncEnv(DBLikeProvider(db), ecProvider)
            val result = dblNested.perform(initialEnv)(Unit)
            controllableExecutionContext.runSingle()
            controllableExecutionContext.runSingle()
            controllableExecutionContext.runSingle()

            controllableExecutionContext.assertEmpty()

            val r1 = result.getAny()
            r1 shouldBe Either.right<Any, String>("is trx+is trx+is trx")
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
    val async: AsyncWrapper<AsyncEnv> = AsyncWrapper()
) :
    TxProvider<DBLike, AsyncEnv>, ExecutionContextProvider by ex, AsyncSupport<AsyncEnv> by async {

    override fun getConnection(): TxConnection<DBLike> = this.db.getConnection()

    override fun setConnectionState(newState: TxConnection<DBLike>) = this.copy(db = db.setConnectionState(newState))

}