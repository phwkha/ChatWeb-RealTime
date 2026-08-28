package com.web.backend.controller;

import com.web.backend.controller.response.UserSummaryResponse;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.PageResponse;
import com.web.backend.service.SearchUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.web.backend.model.postgres.UserEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Search Controller")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchUserController {

    private final SearchUserService searchUserService;

    private static final String SUCCESS_SEARCH_USERS_STRING = "success.search.users";
    private static final String SUCCESS_SEARCH_ADVANCE_STRING = "success.search.advance";

    @Operation(summary = "Search users by keyword", description = "Search users by username, email, first name or last name")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> searchUsers(
            Authentication authentication,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "desc") String sortDir) {

        String currentUsername = null;
        if (authentication != null && authentication.getPrincipal() instanceof UserEntity user) {
            currentUsername = user.getUsername();
        }

        PageResponse<UserSummaryResponse> result = searchUserService.searchUsers(currentUsername, keyword, page, size,
                sortDir);

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_SEARCH_USERS_STRING),
                result));
    }

    @Operation(summary = "Advance search query by specifications", description = "Return list of users")
    @GetMapping(path = "/users/filter")
    public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> advanceSearchWithSpecifications(
            Pageable pageable,
            @RequestParam(required = false) String[] user,
            @RequestParam(required = false) String[] address) {

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_SEARCH_ADVANCE_STRING),
                searchUserService.advanceSearchWithSpecifications(pageable, user, address)));
    }
}
