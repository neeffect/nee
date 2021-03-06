package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.effects.security.oauth.GoogleOpenIdTest.Companion.sampleGoogleToken
import dev.neeffect.nee.effects.time.HasteTimeProvider
import io.fusionauth.jwt.Signer
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.hmac.HMACSigner
import io.fusionauth.jwt.rsa.RSAVerifier
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vavr.collection.HashMap
import io.vavr.control.Try
import io.vavr.kotlin.list
import java.time.Clock
import java.time.Instant
import java.time.ZoneId


internal class JwtEncoderTest : DescribeSpec({
    describe("jwt encoder") {
        val signer: Signer = HMACSigner.newSHA256Signer("too many secrets")

        val encoder = JwtCoder(jwtTestModule)
        val claims = HashMap.of("roles", "admin,nionio")
        it("creates jwt token") {
            val jwt = encoder.createJwt("test subject", claims)
            val encodedJWT = encoder.signJwt(jwt)
            encodedJWT.toString() shouldBe expectedEncodedJwt
        }
        it("decodes jwt token") {
            val encodedJWT = expectedEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.get().subject shouldBe "test subject"
        }
        it("decodes jwt claims") {
            val encodedJWT = expectedEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.get().otherClaims["roles"] shouldBe "admin,nionio"
        }
        it("does not decode bad jwt") {
            val encodedJWT = wrongEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.isLeft shouldBe true
        }


    }
    describe("jwtDecoder") {
        it("decodes sample google token") {
            val jwtDecoder = JWT.getTimeMachineDecoder(haste.now())
            val verifier = GoogleKeys.googleVerifier
            val googleToken = jwtDecoder.decode(sampleGoogleToken, verifier)
            googleToken.subject shouldBe "108874454676244700380"
            println(googleToken)
        }
        it("does not decode outdated google token") {
            val jwtDecoder = JWT.getTimeMachineDecoder(hasteInFuture.now())
            val verifier = GoogleKeys.googleVerifier

            val googleToken = Try.of { jwtDecoder.decode(sampleGoogleToken, verifier) }
            googleToken.isFailure shouldBe true
        }
    }
}) {
    companion object {
        val testConfig = JwtConfig(1000, "neekt takee", "la secret")

        val hasteInFuture = Haste.TimeSource.withFixedClock(
            Clock.fixed(
                Instant.parse("2020-11-20T22:22:03.00Z"),
                ZoneId.of("Europe/Berlin")
            )
        )
        val haste = Haste.TimeSource.withFixedClock(
            Clock.fixed(
                Instant.parse("2020-10-24T22:22:03.00Z"),
                ZoneId.of("Europe/Berlin")
            )
        )
        val jwtTestModule = JwtCoderConfigurationModule(testConfig, HasteTimeProvider(haste))


        const val expectedEncodedJwt =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDM1NzkxMjMsImlhdCI6MTYwMzU3ODEyMywiaXNzIjoibmVla3QgdGFrZWUiLCJzdWIiOiJ0ZXN0IHN1YmplY3QiLCJyb2xlcyI6ImFkbWluLG5pb25pbyJ9.Gf9vlhBncjndb0fG91vfT8Ah0xAq7jKq_r0dDJC-4bo"
        const val wrongEncodedJwt =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDM2NzkxMjMsImlhdCI6MTYwMzU3ODEyMywiaXNzIjoibmVla3QgdGFrZWUiLCJzdWIiOiJ0ZXN0IHN1YmplY3QiLCJyb2xlcyI6ImFkbWluLG5pb25pbyJ9.Gf9vlhBncjndb0fG91vfT8Ah0xAq7jKq_r0dDJC-4bo"

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






