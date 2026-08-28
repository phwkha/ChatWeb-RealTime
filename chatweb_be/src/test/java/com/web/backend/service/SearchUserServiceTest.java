package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.web.backend.common.UserStatus;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.UserRepository;
import com.web.backend.service.impl.SearchUserServiceImpl;

@ExtendWith(MockitoExtension.class)
class SearchUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private SearchUserServiceImpl searchUserService;

    private UserEntity activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new UserEntity();
        activeUser.setUsername("testuser");
        activeUser.setUserStatus(UserStatus.ACTIVE);
    }

    @Test
    void testSearchUsers() {
        Page<UserEntity> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserSummaryResponse(activeUser)).thenReturn(UserSummaryResponse.builder().username("testuser").build());

        PageResponse<UserSummaryResponse> res = searchUserService.searchUsers("current_user", "test", 0, 10, "asc");
        assertEquals(1, res.getTotalElements());
    }

    @Test
    void testSearchUsers_Anonymous() {
        Page<UserEntity> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserSummaryResponse(activeUser)).thenReturn(UserSummaryResponse.builder().username("testuser").build());

        PageResponse<UserSummaryResponse> res = searchUserService.searchUsers(null, "test", 0, 10, "asc");
        assertEquals(1, res.getTotalElements());
    }

    @Test
    void testAdvanceSearchWithSpecifications_NoFilters() {
        Page<UserEntity> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserSummaryResponse(activeUser)).thenReturn(mock(UserSummaryResponse.class));

        PageResponse<UserSummaryResponse> res = searchUserService.advanceSearchWithSpecifications(PageRequest.of(0, 10),
                null, null);
        assertEquals(1, res.getTotalElements());
    }

    @Test
    void testAdvanceSearchWithSpecifications_EmptyArrays() {
        Page<UserEntity> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserSummaryResponse(activeUser)).thenReturn(mock(UserSummaryResponse.class));

        PageResponse<UserSummaryResponse> res = searchUserService.advanceSearchWithSpecifications(PageRequest.of(0, 10),
                new String[] {}, new String[] {});
        assertEquals(1, res.getTotalElements());
    }

    @Test
    void testAdvanceSearchWithSpecifications_WithFilters() {
        Page<UserEntity> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userMapper.toUserSummaryResponse(activeUser)).thenReturn(mock(UserSummaryResponse.class));

        String[] userFilters = { "username:test", "age>18" };
        String[] addressFilters = { "city:hanoi" };

        PageResponse<UserSummaryResponse> res = searchUserService.advanceSearchWithSpecifications(PageRequest.of(0, 10),
                userFilters, addressFilters);
        assertEquals(1, res.getTotalElements());
    }
}
