package com.web.backend.kafka.consumer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.mongodb.bulk.BulkWriteError;
import com.web.backend.common.ActionType;
import com.web.backend.common.MessageStatus;
import com.web.backend.common.MessageType;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongodb.ChatMessage;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

        try {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class);
            for (ChatMessageAvro avro : payloadsToSave) {
                ActionType action = parseActionType(avro.getActionType());
                switch (action) {
                    case EDIT -> {
                        Query query = Query.query(Criteria.where("_id").is(avro.getId()));
                        bulkOps.upsert(query, buildEditUpdate(avro));
                    }
                    case REVOKE -> {
                        Query query = Query.query(Criteria.where("_id").is(avro.getId()));
                        bulkOps.upsert(query, buildRevokeUpdate(avro));
                    }
                    case REACT -> {
                        Query query = Query.query(Criteria.where("_id").is(avro.getId()));
                        bulkOps.upsert(query, buildReactUpdate(avro));
                    }
                    case CREATE -> {
                        ChatMessage entity = messageMapper.toEntity(avro);
                        if (entity.getStatus() == null) {
                            entity.setStatus(MessageStatus.SENT);
                        }
                        bulkOps.insert(entity);
                    }
                }
            }
            bulkOps.execute();
            log.debug("Persisted batch of {} chat message operations to MongoDB", payloadsToSave.size());
        } catch (DuplicateKeyException dke) {
            log.warn("Duplicate key detected in batch, treating as idempotent save: {}", dke.getMessage());
        } catch (BulkOperationException boe) {
            log.warn("Bulk operation exception occurred during message batch persistence: {}", boe.getMessage());
            retryBulkFailuresIndividually(payloadsToSave, boe);
        } catch (Exception ex) {
            log.error("Fatal exception during batch database persistence of {} messages. Delegating to Kafka retry.",
                    payloadsToSave.size(), ex);
            throw ex;
        }
    }

    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}-save-dlt", groupId = "${spring.kafka.topic.chat.messages-save-group-id}-dlt", containerFactory = "dltChatAvroListenerContainerFactory")
    public void handleDltPersistence(ChatMessageAvro message) {
        if (message == null || !MessageType.CHAT.name().equalsIgnoreCase(message.getMessageType())) {
            return;
        }

        try {
            ActionType action = parseActionType(message.getActionType());
            switch (action) {
                case EDIT -> {
                    Query query = Query.query(Criteria.where("_id").is(message.getId()));
                    mongoTemplate.upsert(query, buildEditUpdate(message), ChatMessage.class);
                }
                case REVOKE -> {
                    Query query = Query.query(Criteria.where("_id").is(message.getId()));
                    mongoTemplate.upsert(query, buildRevokeUpdate(message), ChatMessage.class);
                }
                case REACT -> {
                    Query query = Query.query(Criteria.where("_id").is(message.getId()));
                    mongoTemplate.upsert(query, buildReactUpdate(message), ChatMessage.class);
                }
                case CREATE -> {
                    ChatMessage entity = messageMapper.toEntity(message);
                    if (entity.getStatus() == null) {
                        entity.setStatus(MessageStatus.SENT);
                    }
                    mongoTemplate.insert(entity);
                }
            }
            log.info("Successfully recovered and processed message '{}' [action={}] from DLT to MongoDB",
                    message.getId(), action);
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
            ActionType action = parseActionType(payload.getActionType());
            switch (action) {
                case EDIT -> {
                    Query query = Query.query(Criteria.where("_id").is(payload.getId()));
                    mongoTemplate.upsert(query, buildEditUpdate(payload), ChatMessage.class);
                }
                case REVOKE -> {
                    Query query = Query.query(Criteria.where("_id").is(payload.getId()));
                    mongoTemplate.upsert(query, buildRevokeUpdate(payload), ChatMessage.class);
                }
                case REACT -> {
                    Query query = Query.query(Criteria.where("_id").is(payload.getId()));
                    mongoTemplate.upsert(query, buildReactUpdate(payload), ChatMessage.class);
                }
                case CREATE -> {
                    ChatMessage entity = messageMapper.toEntity(payload);
                    if (entity.getStatus() == null) {
                        entity.setStatus(MessageStatus.SENT);
                    }
                    mongoTemplate.insert(entity);
                }
            }
            log.info("Individually processed previously failed message '{}' [action={}]", payload.getId(), action);
        } catch (DuplicateKeyException dke) {
            log.warn("Message '{}' already exists (idempotent), treating as success", payload.getId());
        } catch (Exception ex) {
            log.error("Permanently failed to process message '{}' after individual retry", payload.getId(), ex);
        }
    }

    private ActionType parseActionType(String actionStr) {
        if (actionStr == null || actionStr.isBlank()) {
            return ActionType.CREATE;
        }
        try {
            return ActionType.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ActionType.CREATE;
        }
    }

    private Update buildEditUpdate(ChatMessageAvro avro) {
        ChatMessage entity = messageMapper.toEntity(avro);
        Update update = new Update()
                .set("content", entity.getContent())
                .set("isEdited", true);
        applyBaseOnInsert(update, entity, avro);
        return update;
    }

    private Update buildRevokeUpdate(ChatMessageAvro avro) {
        ChatMessage entity = messageMapper.toEntity(avro);
        Update update = new Update()
                .set("content", "")
                .set("isDeleted", true)
                .unset("fileUrl")
                .unset("fileName")
                .unset("fileSize")
                .unset("reactions");
        applyBaseOnInsert(update, entity, avro);
        return update;
    }

    private Update buildReactUpdate(ChatMessageAvro avro) {
        ChatMessage entity = messageMapper.toEntity(avro);
        Update update = new Update()
                .set("reactions", entity.getReactions())
                .set("isReacted", entity.isReacted());
        applyBaseOnInsert(update, entity, avro);
        return update;
    }

    private void applyBaseOnInsert(Update update, ChatMessage entity, ChatMessageAvro avro) {
        update.setOnInsert("_id", avro.getId())
                .setOnInsert("conversationId", entity.getConversationId())
                .setOnInsert("sender", entity.getSender())
                .setOnInsert("recipient", entity.getRecipient())
                .setOnInsert("timestamp", entity.getTimestamp())
                .setOnInsert("status", MessageStatus.SENT);
        if (entity.getMessageType() != null) {
            update.setOnInsert("messageType", entity.getMessageType());
        }
        if (entity.getContentType() != null) {
            update.setOnInsert("contentType", entity.getContentType());
        }
    }
}
