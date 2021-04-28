package dev.neeffect.nee.effects.tx

import dev.neeffect.nee.effects.utils.invalid

object DummyTxProvider : TxProvider<Nothing, DummyTxProvider> {
    override fun getConnection(): TxConnection<Nothing> = invalid()

    override fun setConnectionState(newState: TxConnection<Nothing>): DummyTxProvider =
        this
}
