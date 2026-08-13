
package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System Message Controller")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/systems")
@Slf4j(topic = "SYSTEM-MESSAGE-CONTROLLER")
public class SystemMessageController {

    private final MessageService messageService;

    private static final String SUCCESS_SYS_GET_MSG_STRING = "success.sys.get_msg";

    @Operation(summary = "Get system messages", description = "API endpoint for get system messages")
    @GetMapping("/message")
    public ResponseEntity<ApiResponse<CursorResponse<MessageSystemResponse>>> getSystemMessages(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching system message");

        CursorResponse<MessageSystemResponse> response = messageService.findSystemMessageWithCursor(cursor, size);

        return ResponseEntity
                .ok(ApiResponse.success(HttpStatus.OK.value(), Translator.tolocale(SUCCESS_SYS_GET_MSG_STRING),
                        response));
    }
}
