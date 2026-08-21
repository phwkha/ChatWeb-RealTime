package com.web.backend.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "{valid.username_empty}")
    private String username;

    @NotBlank(message = "{valid.email_empty}")
    @Email(message = "{valid.email_invalid}")
    private String email;

    @NotBlank(message = "{valid.pwd_empty}")
    @Size(min = 8, message = "{valid.pwd_min_8}")
    private String password;
}