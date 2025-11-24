package com.bos.backend.domain.push

import com.bos.backend.domain.notification.enums.NotificationCategory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PushTemplateTypeTest :
    StringSpec({
        "REPAYMENT_REMINDER_PAYER는 REPAYMENT_DUE 카테고리로 매핑되어야 한다" {
            PushTemplateType.REPAYMENT_REMINDER_PAYER.toNotificationCategory() shouldBe
                NotificationCategory.REPAYMENT_DUE
        }

        "REPAYMENT_REMINDER_PAYEE는 RECEIVABLE_DUE 카테고리로 매핑되어야 한다" {
            PushTemplateType.REPAYMENT_REMINDER_PAYEE.toNotificationCategory() shouldBe
                NotificationCategory.RECEIVABLE_DUE
        }

        "REPAYMENT_TODAY_PAYER는 REPAYMENT_DUE 카테고리로 매핑되어야 한다" {
            PushTemplateType.REPAYMENT_TODAY_PAYER.toNotificationCategory() shouldBe
                NotificationCategory.REPAYMENT_DUE
        }

        "REPAYMENT_TODAY_PAYEE는 RECEIVABLE_DUE 카테고리로 매핑되어야 한다" {
            PushTemplateType.REPAYMENT_TODAY_PAYEE.toNotificationCategory() shouldBe
                NotificationCategory.RECEIVABLE_DUE
        }

        "REPAYMENT_OVERDUE_PAYER는 REPAYMENT_DUE 카테고리로 매핑되어야 한다" {
            PushTemplateType.REPAYMENT_OVERDUE_PAYER.toNotificationCategory() shouldBe
                NotificationCategory.REPAYMENT_DUE
        }

        "REPAYMENT_OVERDUE_PAYEE는 RECEIVABLE_DUE 카테고리로 매핑되어야 한다" {
            PushTemplateType.REPAYMENT_OVERDUE_PAYEE.toNotificationCategory() shouldBe
                NotificationCategory.RECEIVABLE_DUE
        }

        "PARTIAL_REPAYMENT_COMPLETE는 REPAYMENT_COMPLETED 카테고리로 매핑되어야 한다" {
            PushTemplateType.PARTIAL_REPAYMENT_COMPLETE.toNotificationCategory() shouldBe
                NotificationCategory.REPAYMENT_COMPLETED
        }

        "TRANSACTION_COMPLETE는 GENERAL 카테고리로 매핑되어야 한다" {
            PushTemplateType.TRANSACTION_COMPLETE.toNotificationCategory() shouldBe
                NotificationCategory.GENERAL
        }
    })
