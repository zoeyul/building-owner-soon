package com.bos.backend.presentation.transaction.dto

import com.bos.backend.domain.transaction.enum.RelationshipType
import com.bos.backend.domain.transaction.enum.RepaymentType
import com.bos.backend.domain.transaction.enum.TransactionType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

data class CreateTransactionRequestDTO(
    @field:NotNull(message = "거래 유형은 필수입니다")
    val transactionType: TransactionType,
    @field:NotBlank(message = "상대방 이름은 필수입니다")
    @field:Size(max = 12, message = "상대방 이름은 12자 이내여야 합니다")
    val counterpartName: String,
    @field:NotNull(message = "상대방 캐릭터는 필수입니다")
    val counterpartCharacter: CounterpartCharacterDTO,
    @field:NotNull(message = "관계는 필수입니다")
    val relationship: RelationshipType,
    val customRelationship: String?,
    @field:NotNull(message = "거래일은 필수입니다")
    val transactionDate: LocalDate,
    @field:NotNull(message = "전체 금액은 필수입니다")
    @field:DecimalMin(value = "10000", message = "전체 금액은 10000원 이상이어야 합니다")
    val totalAmount: BigDecimal,
    @field:PositiveOrZero(message = "완료된 금액은 0 이상이어야 합니다")
    val completedAmount: BigDecimal?,
    @field:Size(max = 50, message = "메모는 50자 이내여야 합니다")
    val memo: String?,
    @field:NotNull(message = "상환 유형은 필수입니다")
    val repaymentType: RepaymentType,
    val targetDate: LocalDate?,
    @field:PositiveOrZero(message = "월 납부 금액은 0 이상이어야 합니다")
    val monthlyAmount: BigDecimal?,
    @field:Min(value = 1, message = "매달 납부일은 1 이상이어야 합니다")
    @field:Max(value = 31, message = "매달 납부일은 31 이하여야 합니다")
    val paymentDay: Int?,
)

data class CounterpartCharacterDTO(
    @field:NotBlank(message = "얼굴은 필수입니다")
    val face: String,
    @field:NotBlank(message = "손은 필수입니다")
    val hand: String,
    @field:NotBlank(message = "피부색은 필수입니다")
    val skinColor: String,
    @field:NotBlank(message = "앞머리는 필수입니다")
    val bang: String,
    @field:NotBlank(message = "뒷머리는 필수입니다")
    val backHair: String,
    @field:NotBlank(message = "눈은 필수입니다")
    val eyes: String,
    @field:NotBlank(message = "입은 필수입니다")
    val mouth: String,
)
