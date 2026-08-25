package com.web.backend.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.web.backend.common.UserStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private String username;
    private String firstName;
    private String lastName;
    private String avatar;
    private boolean isOnline;
    private UserStatus userStatus;
}
