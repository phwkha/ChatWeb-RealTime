package com.web.backend.model.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterData implements Serializable {
    private String username;
    private String email;
    private String password;
    private Long roleId;
    private String otp;
}
