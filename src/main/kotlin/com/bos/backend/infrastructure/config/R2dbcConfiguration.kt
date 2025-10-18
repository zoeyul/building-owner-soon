package com.bos.backend.infrastructure.config

import com.bos.backend.infrastructure.converter.CharacterReadingConverter
import com.bos.backend.infrastructure.converter.CharacterWritingConverter
import com.bos.backend.infrastructure.converter.CounterpartCharacterReadingConverter
import com.bos.backend.infrastructure.converter.CounterpartCharacterWritingConverter
import com.bos.backend.infrastructure.converter.NotificationCategoryReadConverter
import com.bos.backend.infrastructure.converter.NotificationCategoryWriteConverter
import com.bos.backend.infrastructure.converter.ProfileCharacterReadingConverter
import com.bos.backend.infrastructure.converter.ProfileCharacterWritingConverter
import com.fasterxml.jackson.databind.ObjectMapper
import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.CustomConversions
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.DialectResolver
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Configuration
@EnableR2dbcRepositories(basePackages = ["com.bos.backend.infrastructure.persistence"])
class R2dbcConfiguration {
    // TODO: JPA AttributeConverter와 유사하게 R2DBC에서도 AttributeConverter를 지원하는지 확인 필요
    // TODO: 동작 원리 디깅
    @Bean
    fun r2dbcCustomConversions(
        databaseClient: DatabaseClient,
        objectMapper: ObjectMapper,
    ): R2dbcCustomConversions {
        val dialect = DialectResolver.getDialect(databaseClient.connectionFactory)
        val converters = dialect.converters.toMutableList()
        converters.addAll(R2dbcCustomConversions.STORE_CONVERTERS)
        return R2dbcCustomConversions(
            CustomConversions.StoreConversions.of(dialect.getSimpleTypeHolder(), converters),
            // TODO: Converter 추가될때마다 전역 설정 건드리는 것 수정
            listOf(
                InstantToLocalDateTimeConverter(),
                LocalDateTimeToInstantConverter(),
                CharacterReadingConverter(objectMapper),
                CharacterWritingConverter(objectMapper),
                ProfileCharacterReadingConverter(objectMapper),
                ProfileCharacterWritingConverter(objectMapper),
                CounterpartCharacterReadingConverter(objectMapper),
                CounterpartCharacterWritingConverter(objectMapper),
                NotificationCategoryReadConverter(),
                NotificationCategoryWriteConverter(),
            ),
        )
    }

    @Bean
    fun transactionalOperator(connectionFactory: ConnectionFactory): TransactionalOperator {
        val txManager = R2dbcTransactionManager(connectionFactory)
        return TransactionalOperator.create(txManager)
    }
}

@WritingConverter
class InstantToLocalDateTimeConverter : Converter<Instant, LocalDateTime> {
    override fun convert(source: Instant): LocalDateTime = LocalDateTime.ofInstant(source, ZoneOffset.UTC)
}

@ReadingConverter
class LocalDateTimeToInstantConverter : Converter<LocalDateTime, Instant> {
    override fun convert(source: LocalDateTime): Instant = source.toInstant(ZoneOffset.UTC)
}
