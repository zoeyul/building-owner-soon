package com.bos.backend.application.mapper

import com.bos.backend.domain.user.entity.User
import com.bos.backend.domain.user.entity.UserAuth
import com.bos.backend.presentation.user.dto.UserProfileResponseDTO
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(componentModel = "spring")
interface UserMapper {
    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "userAuth.email", target = "email")
    @Mapping(target = "provider", expression = "java(userAuth.getProviderType().getValue())")
    @Mapping(source = "user.notificationAllowed", target = "isNotificationAllowed")
    @Mapping(source = "user.marketingAgreed", target = "isMarketingAgreed")
    fun toUserProfileDTO(
        user: User,
        userAuth: UserAuth,
    ): UserProfileResponseDTO

    companion object {
        val INSTANCE: UserMapper = Mappers.getMapper(UserMapper::class.java)
    }
}
