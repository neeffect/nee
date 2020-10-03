package pl.setblack.nee.ctx.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import pl.setblack.nee.effects.security.SecurityError

typealias ErrorHandler = (Any) -> OutgoingContent

object DefaultErrorHandler : ErrorHandler {
    override fun invoke(error: Any): OutgoingContent =
        when (error ) {
            is SecurityError -> TextContent(
                text = "security error: ${error.secError()}",
                contentType = ContentType.Text.Plain,
                status = HttpStatusCode.Unauthorized
            )
                else ->  TextContent(
                    text = "error: $error",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.InternalServerError
                )
        }

}
