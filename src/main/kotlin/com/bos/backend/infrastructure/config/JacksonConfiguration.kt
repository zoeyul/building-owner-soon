package com.bos.backend.infrastructure.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Configuration
class JacksonConfiguration {
    @Bean
    fun objectMapper(): ObjectMapper {
        val instantModule =
            SimpleModule().addSerializer(
                Instant::class.java,
                object : JsonSerializer<Instant>() {
                    private val formatter =
                        DateTimeFormatter
                            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                            .withZone(ZoneOffset.UTC)

                    override fun serialize(
                        value: Instant,
                        gen: JsonGenerator,
                        serializers: SerializerProvider,
                    ) {
                        gen.writeString(formatter.format(value))
                    }
                },
            )

        return jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .registerModule(instantModule)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .findAndRegisterModules()
    }
}
