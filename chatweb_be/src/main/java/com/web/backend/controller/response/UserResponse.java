package com.web.backend.controller.response;

import com.web.backend.common.GenderType;
import com.web.backend.common.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

@SuperBuilder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String username;

    private String email;

    private String phone;

    private boolean isOnline;

    private UserStatus userStatus;

    private String role;

    private Set<String> permissions;

    private String firstName;

    private String lastName;

    private String avatar;

    private Date birthday;

    private GenderType gender;

    private Instant createAt;

    private Instant updateAt;


}
