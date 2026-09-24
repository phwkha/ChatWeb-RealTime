package com.web.backend.controller.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AdminCreateUserRequest {

    @NotBlank(message = "{valid.username_empty}")
    private String username;

    @NotBlank(message = "{valid.pwd_empty}")
    @Size(min = 8, message = "{valid.pwd_min_8}")
    private String password;

    @NotBlank(message = "{valid.last_name_empty}")
    private String firstName;
    private String lastName;

    @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$", message = "{valid.phone_invalid}")
    private String phone;

    @NotBlank(message = "{valid.email_empty}")
    @Email(message = "{valid.email_invalid}")
    private String email;

    private Long roleId;
}