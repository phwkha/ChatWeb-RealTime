package com.web.backend.kafka.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.web.backend.common.ActionType;
import com.web.backend.common.MessageType;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.mongodb.ChatMessage;

@ExtendWith(MockitoExtension.class)
class DatabaseWriteBehindConsumerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private DatabaseWriteBehindConsumer consumer;

    private BulkOperations bulkOps;

    @BeforeEach
    void setUp() {
        bulkOps = mock(BulkOperations.class);
    }

    @Test
    void testHandleDbPersistence_MixedActions() {
        ChatMessageAvro createMsg = new ChatMessageAvro();
        createMsg.setId("msg-create");
        createMsg.setMessageType(MessageType.CHAT.name());
        createMsg.setActionType(ActionType.CREATE.name());

        ChatMessageAvro editMsg = new ChatMessageAvro();
        editMsg.setId("msg-edit");
        editMsg.setMessageType(MessageType.CHAT.name());
        editMsg.setContent("Updated text");
        editMsg.setActionType(ActionType.EDIT.name());

        ChatMessageAvro revokeMsg = new ChatMessageAvro();
        revokeMsg.setId("msg-revoke");
        revokeMsg.setMessageType(MessageType.CHAT.name());
        revokeMsg.setActionType(ActionType.REVOKE.name());

        ChatMessageAvro reactMsg = new ChatMessageAvro();
        reactMsg.setId("msg-react");
        reactMsg.setMessageType(MessageType.CHAT.name());
        reactMsg.setActionType(ActionType.REACT.name());

        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ChatMessage.class)).thenReturn(bulkOps);
        when(messageMapper.toEntity(any(ChatMessageAvro.class))).thenAnswer(inv -> {
            ChatMessageAvro avro = inv.getArgument(0);
            ChatMessage entity = new ChatMessage();
            entity.setId(avro.getId());
            entity.setContent(avro.getContent());
            return entity;
        });

        consumer.handleDbPersistence(List.of(createMsg, editMsg, revokeMsg, reactMsg));

        verify(bulkOps, times(1)).insert(any(ChatMessage.class));
        verify(bulkOps, times(3)).upsert(any(Query.class), any(Update.class));
        verify(bulkOps).execute();
    }

    @Test
    void testHandleDltPersistence_CreateAction() {
        ChatMessageAvro createMsg = new ChatMessageAvro();
        createMsg.setId("msg-dlt-create");
        createMsg.setMessageType(MessageType.CHAT.name());
        createMsg.setActionType(ActionType.CREATE.name());

        ChatMessage entity = new ChatMessage();
        entity.setId("msg-dlt-create");
        when(messageMapper.toEntity(createMsg)).thenReturn(entity);

        consumer.handleDltPersistence(createMsg);

        verify(mongoTemplate).insert(entity);
    }

    @Test
    void testHandleDltPersistence_EditAction() {
        ChatMessageAvro editMsg = new ChatMessageAvro();
        editMsg.setId("msg-dlt-edit");
        editMsg.setMessageType(MessageType.CHAT.name());
        editMsg.setContent("Updated");
        editMsg.setActionType(ActionType.EDIT.name());

        ChatMessage entity = new ChatMessage();
        entity.setId("msg-dlt-edit");
        entity.setContent("Updated");
        when(messageMapper.toEntity(editMsg)).thenReturn(entity);

        consumer.handleDltPersistence(editMsg);

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(ChatMessage.class));
    }

    @Test
    void testHandleDltPersistence_RevokeAction() {
        ChatMessageAvro revokeMsg = new ChatMessageAvro();
        revokeMsg.setId("msg-dlt-revoke");
        revokeMsg.setMessageType(MessageType.CHAT.name());
        revokeMsg.setActionType(ActionType.REVOKE.name());

        ChatMessage entity = new ChatMessage();
        entity.setId("msg-dlt-revoke");
        when(messageMapper.toEntity(revokeMsg)).thenReturn(entity);

        consumer.handleDltPersistence(revokeMsg);

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(ChatMessage.class));
    }

    @Test
    void testHandleDltPersistence_ReactAction() {
        ChatMessageAvro reactMsg = new ChatMessageAvro();
        reactMsg.setId("msg-dlt-react");
        reactMsg.setMessageType(MessageType.CHAT.name());
        reactMsg.setActionType(ActionType.REACT.name());

        ChatMessage entity = new ChatMessage();
        entity.setId("msg-dlt-react");
        when(messageMapper.toEntity(reactMsg)).thenReturn(entity);

        consumer.handleDltPersistence(reactMsg);

        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(ChatMessage.class));
    }
}
