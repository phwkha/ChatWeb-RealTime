package com.web.backend.service;

import org.springframework.data.domain.Pageable;

import com.web.backend.controller.response.PageResponse;
import com.web.backend.controller.response.UserSummaryResponse;

public interface SearchUserService {

    PageResponse<UserSummaryResponse> searchUsers(String currentUsername, String keyword, int page, int size,
            String sortDir);

    PageResponse<UserSummaryResponse> advanceSearchWithSpecifications(Pageable pageable, String[] user,
            String[] address);

}
