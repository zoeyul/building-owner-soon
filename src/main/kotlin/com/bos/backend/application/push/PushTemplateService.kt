package com.bos.backend.application.push

import com.bos.backend.domain.push.ExpoPushMessage
import com.bos.backend.domain.push.PushData
import com.bos.backend.domain.push.PushTemplateType
import org.springframework.stereotype.Service
import java.text.NumberFormat
import java.util.Locale

/**
 * 푸시 템플릿 기반 메시지 생성 서비스
 * 각 템플릿 타입에 맞는 푸시 메시지를 생성합니다
 */
@Service
class PushTemplateService {
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    /**
     * 상환 예정 안내 (갚을 사람) 메시지 생성
     * @param expoToken 푸시를 받을 사용자의 Expo 토큰
     * @param nickname 사용자 닉네임
     * @param payeeName 받을 사람 이름
     * @param amount 상환 금액
     * @param transactionId 거래 ID
     * @param scheduleId 상환 스케줄 ID
     * @param notificationId 알림 ID (푸시 클릭 시 읽음 처리용)
     */
    @Suppress("LongParameterList")
    fun createRepaymentReminderPayer(
        expoToken: String,
        nickname: String,
        payeeName: String,
        amount: Long,
        transactionId: Long,
        scheduleId: Long,
        notificationId: Long? = null,
    ): ExpoPushMessage {
        val template = PushTemplateType.REPAYMENT_REMINDER_PAYER
        val body =
            template.bodyTemplate
                .replace("{nickname}", nickname)
                .replace("{payeeName}", payeeName)
                .replace("{amount}", formatAmount(amount))

        return ExpoPushMessage.createOptimized(
            token = expoToken,
            title = template.titleTemplate,
            body = body,
            data = PushData.forRepaymentSchedule(transactionId, scheduleId, notificationId),
        )
    }

    /**
     * 상환 예정 안내 (받을 사람) 메시지 생성
     */
    @Suppress("LongParameterList")
    fun createRepaymentReminderPayee(
        expoToken: String,
        nickname: String,
        payerName: String,
        amount: Long,
        transactionId: Long,
        scheduleId: Long,
        notificationId: Long? = null,
    ): ExpoPushMessage {
        val template = PushTemplateType.REPAYMENT_REMINDER_PAYEE
        val body =
            template.bodyTemplate
                .replace("{nickname}", nickname)
                .replace("{payerName}", payerName)
                .replace("{amount}", formatAmount(amount))

        return ExpoPushMessage.createOptimized(
            token = expoToken,
            title = template.titleTemplate,
            body = body,
            data = PushData.forRepaymentSchedule(transactionId, scheduleId, notificationId),
        )
    }

    /**
     * 상환일 당일 안내 (갚을 사람) 메시지 생성
     */
    @Suppress("LongParameterList")
    fun createRepaymentTodayPayer(
        expoToken: String,
        nickname: String,
        payeeName: String,
        amount: Long,
        transactionId: Long,
        scheduleId: Long,
        notificationId: Long? = null,
    ): ExpoPushMessage {
        val template = PushTemplateType.REPAYMENT_TODAY_PAYER
        val body =
            template.bodyTemplate
                .replace("{nickname}", nickname)
                .replace("{payeeName}", payeeName)
                .replace("{amount}", formatAmount(amount))

        return ExpoPushMessage.createOptimized(
            token = expoToken,
            title = template.titleTemplate,
            body = body,
            data = PushData.forRepaymentSchedule(transactionId, scheduleId, notificationId),
        )
    }

    /**
     * 상환일 당일 안내 (받을 사람) 메시지 생성
     */
    @Suppress("LongParameterList")
    fun createRepaymentTodayPayee(
        expoToken: String,
        nickname: String,
        payerName: String,
        amount: Long,
        transactionId: Long,
        scheduleId: Long,
        notificationId: Long? = null,
    ): ExpoPushMessage {
        val template = PushTemplateType.REPAYMENT_TODAY_PAYEE
        val body =
            template.bodyTemplate
                .replace("{nickname}", nickname)
                .replace("{payerName}", payerName)
                .replace("{amount}", formatAmount(amount))

        return ExpoPushMessage.createOptimized(
            token = expoToken,
            title = template.titleTemplate,
            body = body,
            data = PushData.forRepaymentSchedule(transactionId, scheduleId, notificationId),
        )
    }

    /**
     * 상환 지연 경고 (갚을 사람) 메시지 생성
     */
    @Suppress("LongParameterList")
    fun createRepaymentOverduePayer(
        expoToken: String,
        nickname: String,
        amount: Long,
        transactionId: Long,
        scheduleId: Long,
        notificationId: Long? = null,
    ): ExpoPushMessage {
        val template = PushTemplateType.REPAYMENT_OVERDUE_PAYER
        val body =
            template.bodyTemplate
                .replace("{nickname}", nickname)
                .replace("{amount}", formatAmount(amount))

        return ExpoPushMessage.createOptimized(
            token = expoToken,
            title = template.titleTemplate,
            body = body,
            data = PushData.forRepaymentSchedule(transactionId, scheduleId, notificationId),
        )
    }

    /**
     * 상환 지연 경고 (받을 사람) 메시지 생성
     */
    @Suppress("LongParameterList")
    fun createRepaymentOverduePayee(
        expoToken: String,
        nickname: String,
        amount: Long,
        transactionId: Long,
        scheduleId: Long,
        notificationId: Long? = null,
    ): ExpoPushMessage {
        val template = PushTemplateType.REPAYMENT_OVERDUE_PAYEE
        val body =
            template.bodyTemplate
                .replace("{nickname}", nickname)
                .replace("{amount}", formatAmount(amount))

        return ExpoPushMessage.createOptimized(
            token = expoToken,
            title = template.titleTemplate,
            body = body,
            data = PushData.forRepaymentSchedule(transactionId, scheduleId, notificationId),
        )
    }

    /**
     * 부분 상환 완료 안내 메시지 생성
     */
    @Suppress("LongParameterList")
    fun createPartialRepaymentComplete(
        expoToken: String,
        nickname: String,
        partnerName: String,
        amount: Long,
        progress: Int,
        transactionId: Long,
    ): ExpoPushMessage {
        val template = PushTemplateType.PARTIAL_REPAYMENT_COMPLETE
        val body =
            template.bodyTemplate
                .replace("{nickname}", nickname)
                .replace("{partnerName}", partnerName)
                .replace("{amount}", formatAmount(amount))
                .replace("{progress}", progress.toString())

        return ExpoPushMessage.createOptimized(
            token = expoToken,
            title = template.titleTemplate,
            body = body,
            data = PushData.forTransaction(transactionId),
        )
    }

    /**
     * 거래 내역 최종 완료 안내 메시지 생성
     */
    fun createTransactionComplete(
        expoToken: String,
        nickname: String,
        partnerName: String,
        transactionId: Long,
    ): ExpoPushMessage {
        val template = PushTemplateType.TRANSACTION_COMPLETE
        val body =
            template.bodyTemplate
                .replace("{nickname}", nickname)
                .replace("{partnerName}", partnerName)

        return ExpoPushMessage.createOptimized(
            token = expoToken,
            title = template.titleTemplate,
            body = body,
            data = PushData.forTransaction(transactionId),
        )
    }

    /**
     * 금액을 한국 통화 형식으로 포맷팅
     */
    private fun formatAmount(amount: Long): String {
        return numberFormat.format(amount)
    }
}
