package com.bos.backend.domain.push

import com.bos.backend.domain.notification.enums.NotificationCategory

/**
 * 푸시 알림 템플릿 타입
 * 각 템플릿은 특정 상황에서 발송되는 메시지 형식을 정의합니다
 */
enum class PushTemplateType(
    val description: String,
    val titleTemplate: String,
    val bodyTemplate: String,
    val deepLinkType: DeepLinkType,
) {
    /**
     * 상환 예정 안내 (갚을 사람) - D-2 오전 9시
     */
    REPAYMENT_REMINDER_PAYER(
        description = "상환 예정 안내 (갚을 사람)",
        titleTemplate = "상환 알림",
        bodyTemplate = "{nickname}님, {payeeName}님께 {amount}원을 갚을 날이 2일 남았어요! 미리 준비해 주세요.",
        deepLinkType = DeepLinkType.REPAYMENT_SCHEDULE,
    ),

    /**
     * 상환 예정 안내 (받을 사람) - D-2 오전 9시
     */
    REPAYMENT_REMINDER_PAYEE(
        description = "상환 예정 안내 (받을 사람)",
        titleTemplate = "상환 알림",
        bodyTemplate = "{nickname}님, {payerName}님에게 {amount}원을 받을 날이 2일 남았어요! 확인해 주세요.",
        deepLinkType = DeepLinkType.REPAYMENT_SCHEDULE,
    ),

    /**
     * 상환일 당일 안내 (갚을 사람) - 당일 오전 9시
     */
    REPAYMENT_TODAY_PAYER(
        description = "상환일 당일 안내 (갚을 사람)",
        titleTemplate = "오늘 상환일이에요!",
        bodyTemplate = "{nickname}님, 오늘은 {payeeName}님께 {amount}원을 갚는 날이에요! 늦지 않게 완료해주세요!",
        deepLinkType = DeepLinkType.REPAYMENT_SCHEDULE,
    ),

    /**
     * 상환일 당일 안내 (받을 사람) - 당일 오전 9시
     */
    REPAYMENT_TODAY_PAYEE(
        description = "상환일 당일 안내 (받을 사람)",
        titleTemplate = "오늘 상환일이에요!",
        bodyTemplate = "{nickname}님, 오늘은 {payerName}님에게 {amount}원을 받는 날이에요! 놓치지 마세요!",
        deepLinkType = DeepLinkType.REPAYMENT_SCHEDULE,
    ),

    /**
     * 상환 지연 경고 (갚을 사람) - 익일 오전 9시
     */
    REPAYMENT_OVERDUE_PAYER(
        description = "상환 지연 경고 (갚을 사람)",
        titleTemplate = "상환이 지연되었어요",
        bodyTemplate = "{nickname}님, 어제 예정된 {amount}원 상환이 아직 완료되지 않았어요. 지금 처리해 주세요.",
        deepLinkType = DeepLinkType.REPAYMENT_SCHEDULE,
    ),

    /**
     * 상환 지연 경고 (받을 사람) - 익일 오전 9시
     */
    REPAYMENT_OVERDUE_PAYEE(
        description = "상환 지연 경고 (받을 사람)",
        titleTemplate = "상환이 지연되었어요",
        bodyTemplate = "{nickname}님, 어제 받기로 한 {amount}원이 아직 입금되지 않았어요. 확인해 주세요.",
        deepLinkType = DeepLinkType.REPAYMENT_SCHEDULE,
    ),

    /**
     * 부분 상환 완료 안내 - 상환 1회 완료 시
     */
    PARTIAL_REPAYMENT_COMPLETE(
        description = "부분 상환 완료 안내",
        titleTemplate = "상환 완료!",
        bodyTemplate = "{nickname}님, {partnerName}님과 {amount}원 상환 완료! 진행률이 {progress}%가 되었어요.",
        deepLinkType = DeepLinkType.TRANSACTION,
    ),

    /**
     * 거래 내역 최종 완료 안내 - 해당 거래 내역 100% 완료 시
     */
    TRANSACTION_COMPLETE(
        description = "거래 내역 최종 완료 안내",
        titleTemplate = "모든 상환 완료!",
        bodyTemplate = "{nickname}님, {partnerName}님과의 거래를 모두 완료했어요! 수고하셨습니다.",
        deepLinkType = DeepLinkType.TRANSACTION,
    ),
    ;

    enum class DeepLinkType {
        /** 거래 내역 조회 화면 */
        TRANSACTION,

        /** 거래 내역 하위 상환 스케줄 완료 처리 화면 */
        REPAYMENT_SCHEDULE,
    }

    /**
     * PushTemplateType을 NotificationCategory로 변환
     * 각 푸시 템플릿 타입에 대응하는 알림 카테고리를 반환합니다
     */
    fun toNotificationCategory(): NotificationCategory {
        return when (this) {
            REPAYMENT_REMINDER_PAYER,
            REPAYMENT_TODAY_PAYER,
            REPAYMENT_OVERDUE_PAYER,
            -> NotificationCategory.REPAYMENT_DUE

            REPAYMENT_REMINDER_PAYEE,
            REPAYMENT_TODAY_PAYEE,
            REPAYMENT_OVERDUE_PAYEE,
            -> NotificationCategory.RECEIVABLE_DUE

            PARTIAL_REPAYMENT_COMPLETE -> NotificationCategory.REPAYMENT_COMPLETED

            TRANSACTION_COMPLETE -> NotificationCategory.GENERAL
        }
    }
}
