package com.intellimove.user.service;

import com.intellimove.common.dto.PagedResponse;
import com.intellimove.common.enums.Role;
import com.intellimove.common.exception.BusinessException;
import com.intellimove.common.exception.ResourceNotFoundException;
import com.intellimove.user.dto.CreateUserRequest;
import com.intellimove.user.dto.UpdateUserRequest;
import com.intellimove.user.dto.UserResponse;
import com.intellimove.user.entity.User;
import com.intellimove.user.mapper.UserMapper;
import com.intellimove.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("EMAIL_EXISTS", "Email already registered");
        }

        User user = userMapper.toEntity(request);
        user.setEmail(request.getEmail());
        user.setRole(request.getRole() != null ? request.getRole() : Role.CUSTOMER);
        user = userRepository.save(user);
        log.info("User created: {}", user.getEmail());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        userMapper.updateEntity(request, user);
        user = userRepository.save(user);
        log.info("User updated: {}", user.getEmail());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getAllUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.findAll(pageable);
        return toPagedResponse(users);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.searchUsers(query, pageable);
        return toPagedResponse(users);
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsersByRole(Role role, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.findByRole(role, pageable);
        return toPagedResponse(users);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        userRepository.delete(user);
        log.info("User deleted: {}", user.getEmail());
    }

    private PagedResponse<UserResponse> toPagedResponse(Page<User> page) {
        return PagedResponse.<UserResponse>builder()
                .content(page.getContent().stream().map(userMapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
