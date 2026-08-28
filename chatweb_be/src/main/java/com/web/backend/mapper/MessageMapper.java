package com.web.backend.mapper;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.mongodb.ChatMessage;
import com.web.backend.model.mongodb.SystemMessage;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "edited", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "reacted", ignore = true)
    @Mapping(target = "reactions", ignore = true)
    ChatMessage toEntity(ChatMessageRequest request);

    ChatMessageResponse toResponse(ChatMessage entity);

    MessageSystemResponse systemMessageToResponse(SystemMessage entity);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToString")
    ChatMessageAvro toAvro(ChatMessage entity);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "stringToInstant")
    ChatMessage toEntity(ChatMessageAvro avro);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "stringToInstant")
    ChatMessageResponse avroToResponse(ChatMessageAvro avro);

    @Named("instantToString")
    default String instantToString(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    @Named("stringToInstant")
    default Instant stringToInstant(String dateTimeStr) {
        return (dateTimeStr != null && !dateTimeStr.isEmpty()) ? Instant.parse(dateTimeStr) : null;
    }
}