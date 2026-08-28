package com.web.backend.controller.request;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.GenderType;
import com.web.backend.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSearchUserRequest {

    private String keyword;
    private String role;
    private UserStatus status;
    private GenderType gender;
    private AuthProvider authProvider;
}
