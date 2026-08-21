package com.web.backend.repository;

import com.web.backend.model.mongo.ChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import com.web.backend.common.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest
 class MessageRepositoryTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0").withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @AfterEach
    void cleanUp() {
        messageRepository.deleteAll();
    }

    @Test
    void testFindByConversationId() {
        ChatMessage msg1 = new ChatMessage();
        msg1.setConversationId("conv1");
        msg1.setMessageType(MessageType.CHAT);
        msg1.setContent("hello 1");
        msg1.setTimestamp(LocalDateTime.now());
        mongoTemplate.save(msg1);

        ChatMessage msg2 = new ChatMessage();
        msg2.setConversationId("conv1");
        msg2.setMessageType(MessageType.CHAT);
        msg2.setContent("hello 2");
        msg2.setTimestamp(LocalDateTime.now().minusMinutes(1));
        mongoTemplate.save(msg2);

        List<ChatMessage> messages = messageRepository.findByConversationId("conv1", PageRequest.of(0, 10));

        assertThat(messages).hasSize(2);
    }

    @Test
    void testFindByConversationIdAndTimestampBefore() {
        LocalDateTime now = LocalDateTime.now();

        ChatMessage msg1 = new ChatMessage();
        msg1.setConversationId("conv2");
        msg1.setMessageType(MessageType.CHAT);
        msg1.setContent("hello 1");
        msg1.setTimestamp(now.minusMinutes(10));
        mongoTemplate.save(msg1);

        ChatMessage msg2 = new ChatMessage();
        msg2.setConversationId("conv2");
        msg2.setMessageType(MessageType.CHAT);
        msg2.setContent("hello 2");
        msg2.setTimestamp(now.minusMinutes(5));
        mongoTemplate.save(msg2);

        List<ChatMessage> messages = messageRepository.findByConversationIdAndTimestampBefore("conv2", now.minusMinutes(2), PageRequest.of(0, 10));

        assertThat(messages).hasSize(2);
    }
}
