package pl.setblack.nee.effects.tx

import io.vavr.control.Either
import io.vavr.collection.List
import pl.setblack.nee.effects.security.*

fun scratchPad() {
    val dbEff = TxEffect<DBLike, CombinedProviders>().handleError { e->CombinedError.TxError(e)  as CombinedError}
    val secEff = SecuredRunEffect<String, String, CombinedProviders>("admin")
        .handleError { e -> CombinedError.SecurityError(e)  as CombinedError}
    val combined = secEff.andThen(dbEff)

}


sealed class CombinedError : TxError, SecurityError {
    class TxError(val internal :pl.setblack.nee.effects.tx.TxError ) : CombinedError() {
        override fun txError(): TxErrorType  = internal.txError();
        override fun secError(): SecurityErrorType  = TODO("???")
    }

    class SecurityError(val internal : pl.setblack.nee.effects.security.SecurityError) : CombinedError() {
        override fun txError(): TxErrorType  = TODO()
        override fun secError(): SecurityErrorType = internal.secError()
    }
}

class CombinedProviders(
    val secProvider: SecurityProvider<String, String>,
    val txProvider: TxProvider<DBLike, CombinedProviders>
) : SecurityProvider<String, String> by secProvider,
    TxProvider<DBLike, CombinedProviders> by txProvider {
    override fun setConnectionState(newState: TxConnection<DBLike>): CombinedProviders =
        CombinedProviders(secProvider, txProvider.setConnectionState(newState))
}


class SimpleSecurityProvider<USER, ROLE>(user: USER, roles: List<ROLE>) : SecurityProvider<USER, ROLE> {
    private val ctx = SimpleSecurityContext(user, roles)
    override fun getSecurityContext(): Either<SecurityError, SecurityCtx<USER, ROLE>> = Either.right(ctx)

    internal class SimpleSecurityContext<USER, ROLE>(private val user: USER, private val roles: List<ROLE>) :
        SecurityCtx<USER, ROLE> {
        override fun getCurrentUser(): USER = user


        override fun hasRole(role: ROLE): Boolean = roles.contains(role)

    }
}

