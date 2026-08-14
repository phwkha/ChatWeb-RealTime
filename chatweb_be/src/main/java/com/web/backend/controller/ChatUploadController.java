package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Chat Upload Controller")
@RestController
@RequestMapping("/api/chat/")
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-UPLOAD-CONTROLLER")
public class ChatUploadController {

    private final StorageService storageService;

    private static final String VIDEO_STRING = "video";

    private static final String IMAGE_STRING = "image";

    private static final String SUCCESS_CHAT_UPLOAD_STRING = "success.chat.upload";

    @Operation(summary = "Upload chat image", description = "API endpoint for upload chat image")
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadChatImage(@RequestParam(IMAGE_STRING) MultipartFile file) {
        log.debug("Uploading chat image: name='{}', size={} bytes", file.getOriginalFilename(), file.getSize());
        String url = storageService.upLoadImage(file);
        return ResponseEntity
                .ok(ApiResponse.success(HttpStatus.OK.value(), Translator.tolocale(SUCCESS_CHAT_UPLOAD_STRING), url));
    }

    @Operation(summary = "Upload chat video", description = "API endpoint for upload chat video")
    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadChatVideo(@RequestParam(VIDEO_STRING) MultipartFile file) {
        log.debug("Uploading chat video: name='{}', size={} bytes", file.getOriginalFilename(), file.getSize());
        String url = storageService.uploadVideo(file);
        return ResponseEntity
                .ok(ApiResponse.success(HttpStatus.OK.value(), Translator.tolocale(SUCCESS_CHAT_UPLOAD_STRING), url));
    }
}
