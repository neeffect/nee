package dev.neeffect.nee.ctx.web.oauth

import dev.neeffect.nee.ctx.web.DefaultErrorHandler
import dev.neeffect.nee.ctx.web.DefaultJacksonMapper
import dev.neeffect.nee.ctx.web.util.ApiError
import dev.neeffect.nee.ctx.web.util.RenderHelper
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.utils.merge
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.security.oauth.LoginResult
import dev.neeffect.nee.security.oauth.OauthProviderName
import dev.neeffect.nee.security.oauth.OauthService
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.pipeline.PipelineContext
import io.vavr.control.Either
import io.vavr.control.Try
import io.vavr.kotlin.option

class OauthSupportApi(private val oauthService: OauthService<User, UserRole>) {

    private val renderHelper = RenderHelper(DefaultJacksonMapper.mapper, DefaultErrorHandler)

    fun oauthApi(): Route.() -> Unit = {

        route("/oauth") {
            get("/generateUrl/{provider}") {
                val result = call.request.queryParameters["redirect"].option().toEither<ApiError>(
                    ApiError.WrongArguments("redirect not set")
                ).flatMap { redirectUrl ->
                    extractProvider().flatMap { provider ->
                        oauthService.generateApiCall(provider, redirectUrl).toEither(
                            ApiError.WrongArguments("cannot generate oauth call")
                        )
                    }
                }
                renderHelper.renderResponse(call, result)
            }
            post("/loginUser/{provider}") {

                val loginData = call.receive<OauthLoginData>()
                val result = extractProvider().map { provider ->
                    oauthService.login(loginData.code, loginData.state, loginData.redirectUri, provider)
                        .perform(Unit)
                }.mapLeft { apiError ->
                    Out.left<SecurityErrorType, LoginResult>(
                        SecurityErrorType.MalformedCredentials("$apiError")
                    )
                }.merge()
                renderHelper.serveMessage(call, result)
            }
        }
    }

    private fun PipelineContext<Unit, ApplicationCall>.extractProvider(): Either<ApiError, OauthProviderName> =
        call.parameters["provider"].option().toEither<ApiError>(
            ApiError.WrongArguments("provider not set")
        ).flatMap { providerName ->
            Try.of {
                OauthProviderName.valueOf(providerName)
            }.toEither().mapLeft {
                ApiError.WrongArguments("provider $providerName is unknown")
            }
        }
}

data class OauthLoginData(
    val code: String,
    val state: String,
    val redirectUri: String
)
