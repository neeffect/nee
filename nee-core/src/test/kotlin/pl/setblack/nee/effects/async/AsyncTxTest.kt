package pl.setblack.nee.effects.async

import io.kotlintest.shouldBe
import io.kotlintest.specs.DescribeSpec
import pl.setblack.nee.Nee
import pl.setblack.nee.andThen
import pl.setblack.nee.effects.get
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
        val nestedAction = action.flatMap { prevResult ->
            Nee.constP(combinedEffect) { env ->
                val connection = env.getConnection()
                val res = connection.getResource()
                println(res)
                if (connection.hasTransaction()) {
                    "$prevResult+is trx"
                } else {
                    "$prevResult+no trx"
                }
            }
        }
        it ("works in nested tx" ) {
            val db = DBLike()
            val initialEnv = AsyncEnv(DBLikeProvider(db), ecProvider)
            val result = nestedAction.perform(initialEnv)(Unit)
            controllableExecutionContext.runSingle()
            controllableExecutionContext.runSingle()
            val r1 = result.get()
            r1 shouldBe "is trx+is trx"
        }
    }
}) {
    internal companion object {

        val dbLikeEffect = TxEffect<DBLike, AsyncEnv>()
        val controllableExecutionContext = ControllableExecutionContext()
        val ecProvider = ECProvider(controllableExecutionContext)
        val asyncEffect = AsyncEffect<AsyncEnv>()
        val combinedEffect = asyncEffect.andThen(dbLikeEffect)

    }
}

internal data class AsyncEnv(val db: DBLikeProvider, val ex: ExecutionContextProvider) :
    TxProvider<DBLike, AsyncEnv>, ExecutionContextProvider by ex {

    override fun getConnection(): TxConnection<DBLike> = this.db.getConnection()

    override fun setConnectionState(newState: TxConnection<DBLike>) = this.copy(db = db.setConnectionState(newState))

}