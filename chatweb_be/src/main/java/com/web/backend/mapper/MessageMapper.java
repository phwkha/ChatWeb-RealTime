package com.web.backend.mapper;

import com.web.backend.controller.request.ChatMessageRequest;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.ChatMessage;
import com.web.backend.model.SystemMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    ChatMessage toEntity(ChatMessageRequest request);

    ChatMessageResponse toResponse(ChatMessage entity);

    MessageSystemResponse systemMessageToResponse(SystemMessage entity);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "localDateTimeToString")
    ChatMessageAvro toAvro(ChatMessage entity);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "stringToLocalDateTime")
    ChatMessage toEntity(ChatMessageAvro avro);

    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "stringToLocalDateTime")
    ChatMessageResponse avroToResponse(ChatMessageAvro avro);

    @Named("localDateTimeToString")
    default String localDateTimeToString(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }

    @Named("stringToLocalDateTime")
    default LocalDateTime stringToLocalDateTime(String dateTimeStr) {
        return (dateTimeStr != null && !dateTimeStr.isEmpty()) ? LocalDateTime.parse(dateTimeStr) : null;
    }
}