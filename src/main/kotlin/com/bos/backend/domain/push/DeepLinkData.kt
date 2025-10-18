package com.bos.backend.domain.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeepLinkData(
    val pathname: String,
    val params: DeepLinkParams,
) {
    fun toJsonString(): String {
        return objectMapper.writeValueAsString(this)
    }

    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        fun createRepaymentDeepLink(transactionId: Long): DeepLinkData {
            return DeepLinkData(
                pathname = "/webview/auth",
                params = DeepLinkParams(uri = "/transaction/$transactionId/repayment"),
            )
        }

        fun createTransactionDeepLink(transactionId: Long): DeepLinkData {
            return DeepLinkData(
                pathname = "/webview/auth",
                params = DeepLinkParams(uri = "/transaction/$transactionId"),
            )
        }
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeepLinkParams(
    val uri: String,
)
