package com.web.backend.controller.request;

import com.web.backend.common.GenderType;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;

    @Pattern(regexp = "^(0\\d{9}|\\+84\\d{9})$", message = "{valid.phone_invalid}")
    private String phone;
    private LocalDate birthday;
    private GenderType gender;
}