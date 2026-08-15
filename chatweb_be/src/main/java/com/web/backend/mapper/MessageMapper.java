package com.web.backend.mapper;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.kafka.payload.ChatMessagePayload;
import com.web.backend.model.ChatMessage;
import com.web.backend.model.SystemMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    ChatMessage toEntity(ChatMessageRequest request);

    ChatMessageResponse toResponse(ChatMessage entity);

    MessageSystemResponse systemMessageToResponse(SystemMessage entity);

    @Mapping(target = "localId", ignore = true)
    ChatMessagePayload toPayload(ChatMessage entity);

    ChatMessage toEntity(ChatMessagePayload payload);

    ChatMessageResponse payloadToResponse(ChatMessagePayload payload);
}