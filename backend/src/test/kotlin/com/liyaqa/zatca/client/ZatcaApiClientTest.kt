package com.liyaqa.zatca.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ZatcaApiClientTest {
    private lateinit var wireMock: WireMockServer
    private lateinit var client: ZatcaApiClient

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(wireMockConfig().dynamicPort())
        wireMock.start()
        client = ZatcaApiClient(ObjectMapper(), "http://localhost:${wireMock.port()}")
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `compliance CSID request includes OTP header`() {
        wireMock.stubFor(
            post(urlEqualTo("/compliance"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"requestID":"req1","binarySecurityToken":"token1","secret":"s1"}"""),
                ),
        )

        client.issuanceComplianceCsid("csrBase64", "123456")

        wireMock.verify(
            postRequestedFor(urlEqualTo("/compliance"))
                .withHeader("OTP", equalTo("123456")),
        )
    }

    @Test
    fun `accept-version is V2 on every call`() {
        wireMock.stubFor(
            post(urlEqualTo("/compliance"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"requestID":"r","binarySecurityToken":"t","secret":"s"}"""),
                ),
        )

        client.issuanceComplianceCsid("csr", "otp")

        wireMock.verify(
            postRequestedFor(urlEqualTo("/compliance"))
                .withHeader("Accept-Version", equalTo("V2")),
        )
    }

    @Test
    fun `authorization header is correct Basic encoding`() {
        wireMock.stubFor(
            post(urlEqualTo("/compliance/invoices"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"validationResults":{"status":"PASS"}}"""),
                ),
        )

        client.complianceInvoiceCheck("myToken", "mySecret", "hash", "uuid", "invoice")

        val expectedCredentials =
            java.util.Base64.getEncoder()
                .encodeToString("myToken:mySecret".toByteArray())
        wireMock.verify(
            postRequestedFor(urlEqualTo("/compliance/invoices"))
                .withHeader("Authorization", equalTo("Basic $expectedCredentials")),
        )
    }

    @Test
    fun `reporting request sets Clearance-Status to 0`() {
        wireMock.stubFor(
            post(urlEqualTo("/invoices/reporting/single"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"reportingStatus":"REPORTED","validationResults":{"status":"PASS"}}"""),
                ),
        )

        client.reportSimplifiedInvoice("token", "secret", "hash", "uuid", "invoiceXml")

        wireMock.verify(
            postRequestedFor(urlEqualTo("/invoices/reporting/single"))
                .withHeader("Clearance-Status", equalTo("0")),
        )
    }
}
