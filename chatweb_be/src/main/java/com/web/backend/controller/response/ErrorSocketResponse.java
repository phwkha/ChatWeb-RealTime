package com.web.backend.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.web.backend.common.ErrorCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorSocketResponse {

    private int code;
    private ErrorCode errorCode;
    private String message;
    private Object request;

}
