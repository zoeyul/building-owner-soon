package com.bos.backend.infrastructure.converter

import com.bos.backend.domain.transaction.entity.CounterpartCharacter
import com.bos.backend.domain.user.entity.Character
import com.bos.backend.domain.user.entity.ProfileCharacter
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

@ReadingConverter
class CharacterReadingConverter(
    private val objectMapper: ObjectMapper,
) : Converter<String, Character> {
    override fun convert(source: String): Character = objectMapper.readValue(source, Character::class.java)
}

@WritingConverter
class CharacterWritingConverter(
    private val objectMapper: ObjectMapper,
) : Converter<Character, String> {
    override fun convert(source: Character): String = objectMapper.writeValueAsString(source)
}

@ReadingConverter
class ProfileCharacterReadingConverter(
    private val objectMapper: ObjectMapper,
) : Converter<String, ProfileCharacter> {
    override fun convert(source: String): ProfileCharacter =
        objectMapper.readValue(source, ProfileCharacter::class.java)
}

@WritingConverter
class ProfileCharacterWritingConverter(
    private val objectMapper: ObjectMapper,
) : Converter<ProfileCharacter, String> {
    override fun convert(source: ProfileCharacter): String = objectMapper.writeValueAsString(source)
}

@ReadingConverter
class CounterpartCharacterReadingConverter(
    private val objectMapper: ObjectMapper,
) : Converter<String, CounterpartCharacter> {
    override fun convert(source: String): CounterpartCharacter =
        objectMapper.readValue(source, CounterpartCharacter::class.java)
}

@WritingConverter
class CounterpartCharacterWritingConverter(
    private val objectMapper: ObjectMapper,
) : Converter<CounterpartCharacter, String> {
    override fun convert(source: CounterpartCharacter): String = objectMapper.writeValueAsString(source)
}
