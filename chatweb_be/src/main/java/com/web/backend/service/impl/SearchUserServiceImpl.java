package com.web.backend.service.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.web.backend.common.UserStatus;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserDetailResponse;
import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.mapper.UserMapper;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.repository.UserRepository;
import com.web.backend.repository.specification.AddressSpecification;
import com.web.backend.repository.specification.SearchSpecificationsBuilder;
import com.web.backend.repository.specification.UserSpecification;
import com.web.backend.service.SearchUserService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchUserServiceImpl implements SearchUserService {

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private static final String DESC_STRING = "desc";
    private static final String USERNAME_STRING = "username";
    private static final String EMPTY_STRING = "";
    private static final String WILDCARD_ASTERISK_STRING = "*";

    private static final Pattern SEARCH_PATTERN = Pattern.compile("^(\\w+)([<:>~!])(.*)$");

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> searchUsers(String keyword, int page, int size, String sortDir) {
        Sort.Direction direction = sortDir.equalsIgnoreCase(DESC_STRING) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, USERNAME_STRING));

        Page<UserEntity> pageResult = userRepository.searchUsersByKeyword(keyword, UserStatus.INACTIVE, pageable);

        List<UserSummaryResponse> content = pageResult.getContent().stream()
                .map(userMapper::toUserSummaryResponse)
                .toList();

        return PageResponse.<UserSummaryResponse>builder()
                .content(content)
                .pageNo(pageResult.getNumber())
                .pageSize(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }

    @Override
    public PageResponse<UserDetailResponse> advanceSearchWithSpecifications(Pageable pageable, String[] user,
            String[] address) {

        Specification<UserEntity> finalSpec = Specification.unrestricted();

        SearchSpecificationsBuilder userBuilder = buildSpecifications(user);
        if (!userBuilder.params.isEmpty()) {
            finalSpec = finalSpec.and(new UserSpecification(userBuilder.params));
        }

        SearchSpecificationsBuilder addressBuilder = buildSpecifications(address);
        if (!addressBuilder.params.isEmpty()) {
            finalSpec = finalSpec.and(new AddressSpecification(addressBuilder.params));
        }

        Page<UserEntity> users = userRepository.findAll(finalSpec, Objects.requireNonNull(pageable));
        return convertToPageResponse(users);
    }

    private SearchSpecificationsBuilder buildSpecifications(String[] criteria) {
        SearchSpecificationsBuilder builder = new SearchSpecificationsBuilder();
        if (criteria == null || criteria.length == 0) {
            return builder;
        }
        for (String str : criteria) {
            Matcher matcher = SEARCH_PATTERN.matcher(str.trim());
            if (matcher.matches()) {
                String key = matcher.group(1);
                String op = matcher.group(2);
                String val = matcher.group(3);

                String prefix = EMPTY_STRING;
                String suffix = EMPTY_STRING;
                if (val.startsWith(WILDCARD_ASTERISK_STRING)) {
                    prefix = WILDCARD_ASTERISK_STRING;
                    val = val.substring(1);
                }
                if (val.endsWith(WILDCARD_ASTERISK_STRING) && !val.isEmpty()) {
                    suffix = WILDCARD_ASTERISK_STRING;
                    val = val.substring(0, val.length() - 1);
                }

                builder.with(key, op, val, prefix, suffix);
            }
        }
        return builder;
    }

    private PageResponse<UserDetailResponse> convertToPageResponse(Page<UserEntity> pageResult) {
        List<UserDetailResponse> content = pageResult.getContent().stream()
                .map(userMapper::toUserDetailResponse)
                .toList();
        return PageResponse.<UserDetailResponse>builder()
                .content(content)
                .pageNo(pageResult.getNumber())
                .pageSize(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .last(pageResult.isLast())
                .build();
    }
}
