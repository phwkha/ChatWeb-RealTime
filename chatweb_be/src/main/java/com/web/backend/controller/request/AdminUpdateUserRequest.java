package com.web.backend.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUpdateUserRequest {
    private String firstName;
    private String lastName;

    @Email(message = "{valid.email_invalid}")
    private String email;

    @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$", message = "{valid.phone_invalid}")
    private String phone;
    private Long roleId;
}