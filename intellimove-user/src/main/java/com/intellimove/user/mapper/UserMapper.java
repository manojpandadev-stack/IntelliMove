package com.intellimove.user.mapper;

import com.intellimove.user.dto.CreateUserRequest;
import com.intellimove.user.dto.UpdateUserRequest;
import com.intellimove.user.dto.UserResponse;
import com.intellimove.user.entity.User;

public interface UserMapper {

    User toEntity(CreateUserRequest request);

    UserResponse toResponse(User user);

    void updateEntity(UpdateUserRequest request, User user);
}
