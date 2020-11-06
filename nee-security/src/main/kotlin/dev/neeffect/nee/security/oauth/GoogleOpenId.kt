package dev.neeffect.nee.security.oauth

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.async.InPlaceExecutor
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.security.jwt.MultiVerifier
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.rsa.RSAVerifier
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.vavr.concurrent.Future
import io.vavr.control.Either
import io.vavr.control.Option
import io.vavr.control.Try
import io.vavr.kotlin.list
import io.vavr.kotlin.none
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

interface OauthProvider {
    fun generateApiCall(redirect: String): String

    fun verifyOauthToken(code: String): Nee<Any, SecurityErrorType, Unit, OauthResponse>
}

class GoogleOpenId<USER,ROLE>(
    private val oauthConfigModule: OauthConfigModule<USER,ROLE>
) : OauthProvider {
    private val googleJwtDecoder = JWT.getTimeMachineDecoder(
        oauthConfigModule.jwtConfigModule.timeProvider.getTimeSource().now()
    )

    override fun generateApiCall(redirect: String) =
        apiUrlTemplate(
            oauthConfigModule.config.clientId,
            redirect,
            oauthConfigModule.serverVerifier.generateRandomSignedState(),
            oauthConfigModule.randomGenerator.nextFloat().toString()
        )

    override fun verifyOauthToken(code: String) = Nee.constWithError(NoEffect<Any, SecurityErrorType>()) { _ ->

        Out.Companion.fromFuture(
            Future.fromCompletableFuture(InPlaceExecutor, callGoogle(code)).map { result ->
                Try.of {
                    val decodedIDToken = googleJwtDecoder.decode(result.idToken, GoogleKeys.googleVerifier)
                    val email = Option.of(decodedIDToken.getString("email"))
                    val name = Option.of(decodedIDToken.getString("name"))
                    OauthResponse(result, decodedIDToken.subject, name, email)
                }.toEither().mapLeft<SecurityErrorType> {
                    SecurityErrorType.MalformedCredentials(it.localizedMessage)
                }

            }.orElse {
                Future.successful(Either.left<SecurityErrorType, OauthResponse>(SecurityErrorType.NoSecurityCtx))
            }
        )
    }

    //TODO - what is this stupid GlobalScope here? - clean it or test it
    private fun callGoogle(code: String): CompletableFuture<OauthTokens> = GlobalScope.future {
        val result: OauthTokens = oauthConfigModule.httpClient.submitForm<OauthTokens>(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("code", code)
                append("client_id", oauthConfigModule.config.clientId)
                append("client_secret", oauthConfigModule.config.clientSecret)
                append("redirect_uri", "kpisz panie")
                append("grant_type", "authorization_code")
            },
            encodeInQuery = false,
        )
        result
    }

    companion object {
        fun apiUrlTemplate(clientId: String, redirect: String, state: String, nonce: String) =
            """
        https://accounts.google.com/o/oauth2/v2/auth?
        response_type=code&
        client_id=${clientId}&
        scope=openid&
        redirect_uri=${redirect}&
        state=${state}&
        login_hint=jsmith@example.com&
        nonce=${nonce}""".trimIndent().replace("\n", "")

        object GoogleKeys {
            const val googlePublicKey1 = """
            -----BEGIN CERTIFICATE----- MIIDJjCCAg6gAwIBAgIIF3cBaNBmfgUwDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE AxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe Fw0yMDEwMzEwNDI5NDVaFw0yMDExMTYxNjQ0NDVaMDYxNDAyBgNVBAMTK2ZlZGVy YXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG SIb3DQEBAQUAA4IBDwAwggEKAoIBAQDj8inD/LNXc4HV9LieCecfZxEPLt1lME/x MRqomIgse97c/Zno1KBOv11ssJReM76nC3q390yzahU8N+kzwj7XSD1w76Bw8DlB PNMpweid53QH2nyPMQS9IMGV6PWofT5KAkyihCtslceNq0XhOIA5MIVP7JHA9txq vRBiG9RY1XnbGMS+PjIOeYrjbLzX0tjsfL4aTOTLiJX2aN/qoQWaXFONJ2rG5CjR jrxgclksZLHetCY3Ni3RdjxKjdoYpfP+/iYweRmXraZAbHlT/zQuYH7ePpaMgleK UmaPWlsxToKnqxaMD2lOADEXksPPGmVemNBqzfe+wnmfXGyYbGmjAgMBAAGjODA2 MAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG AQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQC/S0gXu/ExGJsJjZhCcsl75dt97g+i xN6txB+PjqCxFNh7UXJzQbHdRXWzLGtYzE1ZObmDtq7YDi022/Hf4xXf/6ls4Szc ShD7Nad+IXTdmX1lLiY4e+JLhHZ0H0gNhpZUpUAr7KzySgcfufxTH6N1FMtaCDOk f13ulQMCkThTTXzG7eQU2EuHnOMZJ/ttQ7O+XqhrlZT+tBdxKxmO6phZggRWWIh4 zh/9H8a9+RcQc8MKQP1L/WEob42Q383OWUBuoKVveZXyDnIaz5EOqMIIILTAPXBO basHfZtHjuH/Cjx2LWBLrl8tzasKPnYpl7NWWtT6/lH54L92uoyO4l66 -----END CERTIFICATE----- 
            """
            const val googlePublicKey2 = """
            -----BEGIN CERTIFICATE----- MIIDJjCCAg6gAwIBAgIIZUPku00ho9AwDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE AxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe Fw0yMDExMDgwNDI5NDZaFw0yMDExMjQxNjQ0NDZaMDYxNDAyBgNVBAMTK2ZlZGVy YXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG SIb3DQEBAQUAA4IBDwAwggEKAoIBAQC+ONl0GNYNWJt7l/TZVJ3dTyCobOOcYvtl MxRkzHN5gwfXixOb314YmgUW4/pvS2L5UdNt1RzzyM170pjtx+iKG9NLtkiPPDIZ SrZOFXl+xajSBhnCcpsvPRDIBciLtTz3MMS53wrwYiriYrVDWRxcWyM32lrn2oy2 GNUcxQEkZBGofbabPFzQmGQEyzgSUbVQczIKcNxMAUfDfRzuITq5NcEBO00iQvNT LVfmEucpbTQmoP4vbRuxc4A/7/vIaSlIS+9rGuh4GbnwkdQ2G9aSW+wrhL4OHwub 3Xtqk1IJa6Z8NDqmUEmuFEje1CX2TTpl9rJF/7jrLsNqtk/sUk6pAgMBAAGjODA2 MAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG AQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQAwyATYDPr49RRHjr+vw2x1j9RmMZVs ZDwRR+M4XL7DAkyWOOqaxrSHugAe4pM1FzQUsKeagXd5z9XaADunbX8o5zGeavHM VKZPswJu9tdlfF/Vdr58BLN6NxeGpxLZgsFz+/TnY92pY90K+EoNakCxSa/lxhAq 1uNg6hXs5fH6oTDzAGDVQ5KVTF1ChIwqANSe7cyBkEO4zupIdCge9oANQNqUhedW bmrS8gzSFh5Ay3blMmJhWTOAKBlwLR9wuG/KvF721hiZGw+UAUwtOkrpNNhBa/1/ A84bjwuaLiPBnf9HseRpxyuBLu/ANH/GopZ8GD+Y/EjJLBuHWxhLHbfG -----END CERTIFICATE----- 
            """
            const val googlePublicKey3 = """
            -----BEGIN CERTIFICATE----- MIIDJjCCAg6gAwIBAgIIGlvLquGmVf4wDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE AxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe Fw0yMDExMTYwNDI5NDhaFw0yMDEyMDIxNjQ0NDhaMDYxNDAyBgNVBAMTK2ZlZGVy YXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG SIb3DQEBAQUAA4IBDwAwggEKAoIBAQCxXXnz4xD7n6w/aJMmJuIxqnW6Dy01j3uV o653dJ7/eN3gg2rfo3CEumBTcULlIJ8k6z3B6FMvO/+EG6j6xbQk2MAS0wQT5IO3 HnjzqCPKYNH7kjC/tuC3bm0PRwOCJuhku3VEuf6c/5XfOBgdlr+z3MuOk3ICuxZZ xKHq1Z7ZHzJboGpLyXj/3PyOQp7IDBaZ2mRjwG0pLTNn3KWOILEq+zwIqN8eatrK DjmxnxXX5pFyO1HYQLEBMeMTwv3r+g111n6uPZrF/a9OaeTHc68gyDHS1nTJwwbp bLzDHFpHmKvYtXcaTJ+HvZTu0jxDWyiQ+YfobrYlx241jrqMRCW9AgMBAAGjODA2 MAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG AQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQALt6kq8n+pkaXs/1COusSEATWuaCh4 GPjIBGWRaNWwtZq7hcI5L5qkx/76IRI5L5lzHGbu9Z62/zO1wjeKI0JClz1vBakN TgOLlPdFq6H/5Mmuxmt+/vHSxO27+99hCT5EZ16OHPY2gftGjOLJiF5dLXycCcUS mQWHjHSNM3zFClLrcpcluJbeZwGdIobA407tH5s2OFGUystyCZADLY5CqR4LCd0a b3FonCAqyLMqXuTcHkY6OxbPvBlJx4sY3cb/hmC1FkG8om0cvH8jmRxnW2492Mqd Sj45lJJUMVUDhZQdrCZXpKVKnIhq1O3gAkLsUiD3Zd8+FkdjDNTNbwao -----END CERTIFICATE----- 
            """

            val googleVerifier = MultiVerifier(
                list(
                    RSAVerifier.newVerifier(googlePublicKey1),
                    RSAVerifier.newVerifier(googlePublicKey2),
                    RSAVerifier.newVerifier(googlePublicKey3)
                )
            )

        }


    }

}

data class OauthTokens(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("id_token")
    val idToken: String,
    val refreshToken: Option<String>
) {
    @JsonCreator
    constructor(
        @JsonProperty("access_token")
        accessToken: String,
        @JsonProperty("id_token")
        idToken: String,
        @JsonProperty("refresh_token")
        refreshToken: String
    ) : this(accessToken, idToken, Option.some(refreshToken))
}


data class OauthResponse(
    val tokens: OauthTokens,
    val subject: String,
    val displayName: Option<String>,
    val email: Option<String>
)
