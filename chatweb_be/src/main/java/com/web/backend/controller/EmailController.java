package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.EmailRequest;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.service.EmailService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Email Controller")
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
@Slf4j(topic = "EMAIL-CONTROLLER")
public class EmailController {

    private final EmailService emailService;

    private static final String SUCCESS_EMAIL_SENDING_STRING = "success.email.sending";

    @Operation(summary = "Send email", description = "API endpoint for send email")
    @PostMapping("/send")
    @PreAuthorize("hasAuthority('SEND_EMAIL')")
    public ResponseEntity<ApiResponse<Void>> sendEmail(@RequestBody @Valid EmailRequest request) {
        log.debug("Email dispatch requested for recipient '{}'", request.getTo());

        emailService.sendTextEmail(request.getTo(), request.getSubject(), request.getText());

        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                Translator.tolocale(SUCCESS_EMAIL_SENDING_STRING),
                null));
    }
}