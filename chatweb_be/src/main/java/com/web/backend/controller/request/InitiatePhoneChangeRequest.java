package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class InitiatePhoneChangeRequest {
    @NotBlank
    @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$", message = "{valid.phone_invalid}")
    private String newPhone;

    @NotBlank(message = "{valid.require_pwd}")
    private String currentPassword;
}