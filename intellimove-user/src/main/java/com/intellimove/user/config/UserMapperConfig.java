package com.intellimove.user.config;

import com.intellimove.user.dto.CreateUserRequest;
import com.intellimove.user.dto.UpdateUserRequest;
import com.intellimove.user.dto.UserResponse;
import com.intellimove.user.entity.User;
import com.intellimove.user.mapper.UserMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserMapperConfig {

    @Bean
    public UserMapper userMapper() {
        return new UserMapper() {
            @Override
            public User toEntity(CreateUserRequest request) {
                if (request == null) return null;
                return User.builder()
                        .email(request.getEmail())
                        .firstName(request.getFirstName())
                        .lastName(request.getLastName())
                        .phoneNumber(request.getPhoneNumber())
                        .build();
            }

            @Override
            public UserResponse toResponse(User user) {
                if (user == null) return null;
                return UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .phoneNumber(user.getPhoneNumber())
                        .role(user.getRole())
                        .enabled(user.isEnabled())
                        .profileImageUrl(user.getProfileImageUrl())
                        .address(user.getAddress())
                        .city(user.getCity())
                        .createdAt(user.getCreatedAt())
                        .updatedAt(user.getUpdatedAt())
                        .build();
            }

            @Override
            public void updateEntity(UpdateUserRequest request, User user) {
                if (request == null || user == null) return;
                if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
                if (request.getLastName() != null) user.setLastName(request.getLastName());
                if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
                if (request.getAddress() != null) user.setAddress(request.getAddress());
                if (request.getCity() != null) user.setCity(request.getCity());
                if (request.getProfileImageUrl() != null) user.setProfileImageUrl(request.getProfileImageUrl());
            }
        };
    }
}
