package com.web.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLanguageRequest {

    @NotBlank(message = "{valid.language_required}")
    @Pattern(regexp = "^(vi|en|ja)$", message = "{valid.language_invalid}")
    private String language;
}
