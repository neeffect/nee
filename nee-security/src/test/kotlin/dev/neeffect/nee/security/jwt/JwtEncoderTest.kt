package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import io.fusionauth.jwt.JWTDecoder
import io.fusionauth.jwt.Signer
import io.fusionauth.jwt.hmac.HMACSigner
import io.fusionauth.jwt.rsa.RSAVerifier
import io.fusionauth.security.DefaultCryptoProvider
import io.haste.Haste
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import io.vavr.collection.HashMap


internal class JwtEncoderTest:DescribeSpec({
    describe("jwt encoder") {
        val signer: Signer = HMACSigner.newSHA256Signer("too many secrets")

        val encoder = JwtCoder(jwtTestModule)
        val claims=HashMap.of("roles","admin,nionio")
        it("creates jwt token") {
            val jwt = encoder.createJwt("test subject", claims)
            val encodedJWT = encoder.signJwt(jwt)
            encodedJWT.toString() shouldBe expectedEncodedJwt
        }
        it("decodes jwt token") {
            val encodedJWT = expectedEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.subject shouldBe "test subject"
        }
        it("decodes jwt claims") {
            val encodedJWT = expectedEncodedJwt
            val jwt = encoder.decodeJwt(encodedJWT)
            jwt.otherClaims["roles"] shouldBe "admin,nionio"
        }


    }
    describe("jwtDecoder") {
        it ("decodes sample google token") {
            val jwtDecoder = JWTDecoder(clock)
            val verifier1 = RSAVerifier.newVerifier(googlePublicKey1)
            val verifier2 = RSAVerifier.newVerifier(googlePublicKey2)
            val verifier3 = RSAVerifier.newVerifier(googlePublicKey3)
            val googleToken = jwtDecoder.decode(sampleGoogleToken, verifier2)
            println(googleToken)
        }
    }
}) {
    companion object {
        val testConfig = JwtConfig(1000, "neekt takee", "la secret")
        val clock = Clock.fixed(Instant.parse("2020-10-24T22:22:03.00Z"), ZoneId.of("Europe/Berlin"))
        val haste = Haste.TimeSource.withFixedClock(clock)
        val jwtTestModule = object : JwtCoderConfigurationModule(testConfig) {
            override val timeProvider: TimeProvider
                get() = HasteTimeProvider(haste)
        }

        const val expectedEncodedJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE2MDM1NzkxMjMsImlhdCI6MTYwMzU3ODEyMywiaXNzIjoibmVla3QgdGFrZWUiLCJzdWIiOiJ0ZXN0IHN1YmplY3QiLCJyb2xlcyI6ImFkbWluLG5pb25pbyJ9.Gf9vlhBncjndb0fG91vfT8Ah0xAq7jKq_r0dDJC-4bo"
        const val  sampleGoogleToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImQ5NDZiMTM3NzM3Yjk3MzczOGU1Mjg2YzIwOGI2NmU3YTM5ZWU3YzEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI4OTA0Mzg5MDc5NzYtdmRrZG9kcmo4NjE5dXZxaTZhcG5ocHQ2MTFoMWY5OGguYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMDg4NzQ0NTQ2NzYyNDQ3MDAzODAiLCJlbWFpbCI6ImpyYXRhanNraUBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6InFIR21SMUE3OUhqdEl5cW5MTl9ya2ciLCJuYW1lIjoiSmFyZWsgUmF0YWpza2kiLCJwaWN0dXJlIjoiaHR0cHM6Ly9saDQuZ29vZ2xldXNlcmNvbnRlbnQuY29tLy1lWHB2TlJLdVJyZy9BQUFBQUFBQUFBSS9BQUFBQUFBQUFBQS9BTVp1dWNtbTRzS0RCenJhakpXN0NTSVkxeUF4VzZsUGp3L3M5Ni1jL3Bob3RvLmpwZyIsImdpdmVuX25hbWUiOiJKYXJlayIsImZhbWlseV9uYW1lIjoiUmF0YWpza2kiLCJsb2NhbGUiOiJwbCIsImlhdCI6MTYwNTUyOTYyMiwiZXhwIjoxNjA1NTMzMjIyfQ.TfFiTZ4xLYcBllGqHZPAyeUn5Vo5t-hHmyja_upx-6HuXIY4RKxA_IYHX28MsCKD0hX9hX-LiZqIuZus-NKimguHmxbHxweUassraPidI-UmqTkrccFWYXE1wqLvpm_He9fwTf6imFmXnAPDT61bhTm2HQAgwZ_HOVsd8uk1j4uuIM-DHU7ndOtX88KXoXDfILKSAzOUcVwWgUgCmjuGSpd6RQ4JH7remBNcQCs0qQ7WZPKNsY1xKHj7y4LMjPpKFb3vGo1omxTeHCMmmgzS3sf7SAomqbRGUwGWi92HWv560FfXDjFf59zzmgWoNsauRXXjlMNK9QPrj7gUriq2mQ"
        const val googlePublicKey1 = """
            -----BEGIN CERTIFICATE----- MIIDJjCCAg6gAwIBAgIIF3cBaNBmfgUwDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE AxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe Fw0yMDEwMzEwNDI5NDVaFw0yMDExMTYxNjQ0NDVaMDYxNDAyBgNVBAMTK2ZlZGVy YXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG SIb3DQEBAQUAA4IBDwAwggEKAoIBAQDj8inD/LNXc4HV9LieCecfZxEPLt1lME/x MRqomIgse97c/Zno1KBOv11ssJReM76nC3q390yzahU8N+kzwj7XSD1w76Bw8DlB PNMpweid53QH2nyPMQS9IMGV6PWofT5KAkyihCtslceNq0XhOIA5MIVP7JHA9txq vRBiG9RY1XnbGMS+PjIOeYrjbLzX0tjsfL4aTOTLiJX2aN/qoQWaXFONJ2rG5CjR jrxgclksZLHetCY3Ni3RdjxKjdoYpfP+/iYweRmXraZAbHlT/zQuYH7ePpaMgleK UmaPWlsxToKnqxaMD2lOADEXksPPGmVemNBqzfe+wnmfXGyYbGmjAgMBAAGjODA2 MAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG AQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQC/S0gXu/ExGJsJjZhCcsl75dt97g+i xN6txB+PjqCxFNh7UXJzQbHdRXWzLGtYzE1ZObmDtq7YDi022/Hf4xXf/6ls4Szc ShD7Nad+IXTdmX1lLiY4e+JLhHZ0H0gNhpZUpUAr7KzySgcfufxTH6N1FMtaCDOk f13ulQMCkThTTXzG7eQU2EuHnOMZJ/ttQ7O+XqhrlZT+tBdxKxmO6phZggRWWIh4 zh/9H8a9+RcQc8MKQP1L/WEob42Q383OWUBuoKVveZXyDnIaz5EOqMIIILTAPXBO basHfZtHjuH/Cjx2LWBLrl8tzasKPnYpl7NWWtT6/lH54L92uoyO4l66 -----END CERTIFICATE----- 
            """
        const val googlePublicKey2 ="""
            -----BEGIN CERTIFICATE----- MIIDJjCCAg6gAwIBAgIIZUPku00ho9AwDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE AxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe Fw0yMDExMDgwNDI5NDZaFw0yMDExMjQxNjQ0NDZaMDYxNDAyBgNVBAMTK2ZlZGVy YXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG SIb3DQEBAQUAA4IBDwAwggEKAoIBAQC+ONl0GNYNWJt7l/TZVJ3dTyCobOOcYvtl MxRkzHN5gwfXixOb314YmgUW4/pvS2L5UdNt1RzzyM170pjtx+iKG9NLtkiPPDIZ SrZOFXl+xajSBhnCcpsvPRDIBciLtTz3MMS53wrwYiriYrVDWRxcWyM32lrn2oy2 GNUcxQEkZBGofbabPFzQmGQEyzgSUbVQczIKcNxMAUfDfRzuITq5NcEBO00iQvNT LVfmEucpbTQmoP4vbRuxc4A/7/vIaSlIS+9rGuh4GbnwkdQ2G9aSW+wrhL4OHwub 3Xtqk1IJa6Z8NDqmUEmuFEje1CX2TTpl9rJF/7jrLsNqtk/sUk6pAgMBAAGjODA2 MAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG AQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQAwyATYDPr49RRHjr+vw2x1j9RmMZVs ZDwRR+M4XL7DAkyWOOqaxrSHugAe4pM1FzQUsKeagXd5z9XaADunbX8o5zGeavHM VKZPswJu9tdlfF/Vdr58BLN6NxeGpxLZgsFz+/TnY92pY90K+EoNakCxSa/lxhAq 1uNg6hXs5fH6oTDzAGDVQ5KVTF1ChIwqANSe7cyBkEO4zupIdCge9oANQNqUhedW bmrS8gzSFh5Ay3blMmJhWTOAKBlwLR9wuG/KvF721hiZGw+UAUwtOkrpNNhBa/1/ A84bjwuaLiPBnf9HseRpxyuBLu/ANH/GopZ8GD+Y/EjJLBuHWxhLHbfG -----END CERTIFICATE----- 
            """
        const val  googlePublicKey3 = """
            -----BEGIN CERTIFICATE----- MIIDJjCCAg6gAwIBAgIIGlvLquGmVf4wDQYJKoZIhvcNAQEFBQAwNjE0MDIGA1UE AxMrZmVkZXJhdGVkLXNpZ25vbi5zeXN0ZW0uZ3NlcnZpY2VhY2NvdW50LmNvbTAe Fw0yMDExMTYwNDI5NDhaFw0yMDEyMDIxNjQ0NDhaMDYxNDAyBgNVBAMTK2ZlZGVy YXRlZC1zaWdub24uc3lzdGVtLmdzZXJ2aWNlYWNjb3VudC5jb20wggEiMA0GCSqG SIb3DQEBAQUAA4IBDwAwggEKAoIBAQCxXXnz4xD7n6w/aJMmJuIxqnW6Dy01j3uV o653dJ7/eN3gg2rfo3CEumBTcULlIJ8k6z3B6FMvO/+EG6j6xbQk2MAS0wQT5IO3 HnjzqCPKYNH7kjC/tuC3bm0PRwOCJuhku3VEuf6c/5XfOBgdlr+z3MuOk3ICuxZZ xKHq1Z7ZHzJboGpLyXj/3PyOQp7IDBaZ2mRjwG0pLTNn3KWOILEq+zwIqN8eatrK DjmxnxXX5pFyO1HYQLEBMeMTwv3r+g111n6uPZrF/a9OaeTHc68gyDHS1nTJwwbp bLzDHFpHmKvYtXcaTJ+HvZTu0jxDWyiQ+YfobrYlx241jrqMRCW9AgMBAAGjODA2 MAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBYGA1UdJQEB/wQMMAoGCCsG AQUFBwMCMA0GCSqGSIb3DQEBBQUAA4IBAQALt6kq8n+pkaXs/1COusSEATWuaCh4 GPjIBGWRaNWwtZq7hcI5L5qkx/76IRI5L5lzHGbu9Z62/zO1wjeKI0JClz1vBakN TgOLlPdFq6H/5Mmuxmt+/vHSxO27+99hCT5EZ16OHPY2gftGjOLJiF5dLXycCcUS mQWHjHSNM3zFClLrcpcluJbeZwGdIobA407tH5s2OFGUystyCZADLY5CqR4LCd0a b3FonCAqyLMqXuTcHkY6OxbPvBlJx4sY3cb/hmC1FkG8om0cvH8jmRxnW2492Mqd Sj45lJJUMVUDhZQdrCZXpKVKnIhq1O3gAkLsUiD3Zd8+FkdjDNTNbwao -----END CERTIFICATE----- 
            """
    }
}




