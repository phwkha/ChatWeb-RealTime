package com.web.backend.controller;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.request.SaveKeyRequest;
import com.web.backend.controller.request.SavePublicKeyRequest;
import com.web.backend.controller.response.ApiResponse;
import com.web.backend.controller.response.RsaKeyResponse;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.KeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Key Controller")
@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
@Slf4j(topic = "KEY-CONTROLLER")
public class KeyController {

        private final KeyService keyService;

        private static final String SUCCESS_KEY_GET_RSA_STRING = "success.key.get_rsa";
        private static final String SUCCESS_KEY_SAVE_RSA_STRING = "success.key.save_rsa";
        private static final String SUCCESS_KEY_GET_PUB_STRING = "success.key.get_pub";
        private static final String SUCCESS_KEY_SAVE_PUB_STRING = "success.key.save_pub";

        @Operation(summary = "Get rsa key", description = "API endpoint for get rsa key")
        @GetMapping("/rsa")
        public ResponseEntity<ApiResponse<RsaKeyResponse>> getRsaKey(Authentication auth) {
                UserEntity user = (UserEntity) auth.getPrincipal();

                return ResponseEntity.ok(
                                ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_KEY_GET_RSA_STRING),
                                                RsaKeyResponse.builder()
                                                                .privateKey(keyService.getRsaKey(user.getUsername()))
                                                                .build()));
        }

        @Operation(summary = "Save rsa key", description = "API endpoint for save rsa key")
        @PostMapping("/rsa")
        public ResponseEntity<ApiResponse<Void>> saveRsaKey(
                        Authentication auth,
                        @RequestBody @Valid SaveKeyRequest request) {

                UserEntity user = (UserEntity) auth.getPrincipal();

                log.debug("User '{}' saving encrypted RSA key", user.getUsername());

                keyService.saveRsaKey(user.getUsername(), request.getKey());

                return ResponseEntity.ok(
                                ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_KEY_SAVE_RSA_STRING), null));
        }

        @Operation(summary = "Get public key", description = "API endpoint for get public key")
        @GetMapping("/public-key/{username}")
        public ResponseEntity<ApiResponse<String>> getPublicKey(@PathVariable String username) {
                return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                                Translator.tolocale(SUCCESS_KEY_GET_PUB_STRING),
                                keyService.getPublicKey(username)));
        }

        @Operation(summary = "Save public key", description = "API endpoint for save public key")
        @PostMapping("/public-key")
        public ResponseEntity<ApiResponse<Void>> savePublicKey(Authentication authentication,
                        @RequestBody @Valid SavePublicKeyRequest request) {
                UserEntity userEntityPrincipal = (UserEntity) authentication.getPrincipal();
                log.debug("User '{}' saving public key", userEntityPrincipal.getUsername());
                keyService.savePublicKey(userEntityPrincipal.getUsername(), request.getPublicKey());
                return ResponseEntity
                                .ok(ApiResponse.success(HttpStatus.OK.value(),
                                                Translator.tolocale(SUCCESS_KEY_SAVE_PUB_STRING), null));
        }

}