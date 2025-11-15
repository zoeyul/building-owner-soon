package com.bos.backend.domain.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Expo Push Notification의 data 페이로드
 * 앱에서 푸시 클릭 시 이동할 화면 정보를 포함합니다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PushData(
    /**
     * 웹뷰 경로 (고정값: "/webview/auth")
     */
    val pathname: String = "/webview/auth",
    /**
     * 딥링크 URI
     * - 거래 내역: "/transaction/{transactionId}"
     * - 상환 스케줄: "/transaction/{transactionId}/repayment"
     */
    val uri: String,
    /**
     * 상환 스케줄 ID (상환 스케줄 화면인 경우에만 사용)
     * 중첩 data 구조로 전달: { "data": { "scheduleId": 225 } }
     */
    val data: ScheduleData? = null,
) {
    companion object {
        /**
         * 거래 내역 조회 화면으로 이동하는 데이터 생성
         */
        fun forTransaction(transactionId: Long): PushData {
            return PushData(
                uri = "/transaction/$transactionId",
                data = null,
            )
        }

        /**
         * 상환 스케줄 완료 처리 화면으로 이동하는 데이터 생성
         */
        fun forRepaymentSchedule(
            transactionId: Long,
            scheduleId: Long,
        ): PushData {
            return PushData(
                uri = "/transaction/$transactionId/repayment",
                data = ScheduleData(scheduleId = scheduleId),
            )
        }
    }
}

/**
 * 상환 스케줄 관련 추가 데이터
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScheduleData(
    @JsonProperty("scheduleId")
    val scheduleId: Long,
)
