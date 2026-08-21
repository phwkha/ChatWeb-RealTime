package com.web.backend.kafka.consumer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.mongodb.bulk.BulkWriteError;
import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.ChatMessage;
import com.web.backend.mapper.MessageMapper;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.dao.DuplicateKeyException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "DATABASE-WRITE-BEHIND-CONSUMER")
public class DatabaseWriteBehindConsumer {

    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;

    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-save-group-id}", containerFactory = "batchChatAvroListenerContainerFactory")
    public void handleDbPersistence(List<ChatMessageAvro> messagePayloads) {
        if (messagePayloads == null || messagePayloads.isEmpty()) {
            return;
        }

        List<ChatMessageAvro> payloadsToSave = messagePayloads.stream()
                .filter(msg -> MessageType.CHAT.name().equalsIgnoreCase(msg.getMessageType()))
                .toList();

        if (payloadsToSave.isEmpty()) {
            return;
        }

        List<ChatMessage> entitiesToSave = payloadsToSave.stream()
                .map(avro -> {
                    ChatMessage entity = messageMapper.toEntity(avro);
                    entity.setStatus(MessageStatus.SENT);
                    return entity;
                })
                .toList();

        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
            bulkOps.insert(entitiesToSave);
            bulkOps.execute();
            log.debug("Persisted batch of {} chat messages to MongoDB", entitiesToSave.size());
        } catch (DuplicateKeyException dke) {
            log.warn("Duplicate key detected in batch, treating as idempotent save: {}", dke.getMessage());
        } catch (BulkOperationException boe) {
            log.warn("Bulk operation exception occurred during message batch persistence: {}", boe.getMessage());
            retryBulkFailuresIndividually(payloadsToSave, boe);
        } catch (Exception ex) {
            log.error("Fatal exception during batch database persistence of {} messages. Delegating to Kafka retry.",
                    entitiesToSave.size(), ex);
            throw ex;
        }
    }

    @KafkaListener(topics = "chat.messages.dlt", groupId = "${spring.kafka.topic.chat.messages-save-group-id}-dlt", containerFactory = "dltChatAvroListenerContainerFactory")
    public void handleDltPersistence(ChatMessageAvro message) {
        if (message == null || !MessageType.CHAT.name().equalsIgnoreCase(message.getMessageType())) {
            return;
        }

        try {
            ChatMessage entity = messageMapper.toEntity(message);
            entity.setStatus(MessageStatus.SENT);
            mongoTemplate.save(entity);
            log.info("Successfully recovered and saved message '{}' from DLT to MongoDB", message.getId());
        } catch (DuplicateKeyException dke) {
            log.warn("Message '{}' in DLT was already saved (idempotent)", message.getId());
        } catch (Exception ex) {
            log.error("Failed to persist message '{}' from DLT, delegating to DLT retry backoff", message.getId(), ex);
            throw ex;
        }
    }

    private void retryBulkFailuresIndividually(List<ChatMessageAvro> payloadsToSave, BulkOperationException boe) {
        if (boe.getErrors() == null || boe.getErrors().isEmpty()) {
            return;
        }

        Set<Integer> processedIndices = new HashSet<>();
        for (BulkWriteError error : boe.getErrors()) {
            if (error.getCode() == 11000) {
                log.debug("Duplicate key at index {} ignored (idempotent write)", error.getIndex());
            } else {
                int index = error.getIndex();
                if (index >= 0 && index < payloadsToSave.size() && processedIndices.add(index)) {
                    saveIndividually(payloadsToSave.get(index));
                }
            }
        }
    }

    private void saveIndividually(ChatMessageAvro payload) {
        try {
            ChatMessage entity = messageMapper.toEntity(payload);
            entity.setStatus(MessageStatus.SENT);
            mongoTemplate.save(entity);
            log.info("Individually saved previously failed message '{}'", payload.getId());
        } catch (DuplicateKeyException dke) {
            log.warn("Message '{}' already exists (idempotent), treating as success", payload.getId());
        } catch (Exception ex) {
            log.error("Permanently failed to save message '{}' after individual retry", payload.getId(), ex);
        }
    }
}
