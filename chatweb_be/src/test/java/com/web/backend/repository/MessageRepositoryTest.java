package com.web.backend.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import com.web.backend.common.MessageType;
import com.web.backend.model.mongodb.ChatMessage;
import com.web.backend.model.mongodb.ReadReceipt;
import com.web.backend.repository.projection.UnreadCountProjection;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest
@ActiveProfiles("test")
class MessageRepositoryTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0");

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
        mongoTemplate.dropCollection(ReadReceipt.class);
    }

    @Test
    void testFindByConversationId() {
        ChatMessage msg1 = new ChatMessage();
        msg1.setConversationId("conv1");
        msg1.setMessageType(MessageType.CHAT);
        msg1.setContent("hello 1");
        msg1.setTimestamp(Instant.now());
        mongoTemplate.save(msg1);

        ChatMessage msg2 = new ChatMessage();
        msg2.setConversationId("conv1");
        msg2.setMessageType(MessageType.CHAT);
        msg2.setContent("hello 2");
        msg2.setTimestamp(Instant.now().minusSeconds(60));
        mongoTemplate.save(msg2);

        List<ChatMessage> messages = messageRepository.findByConversationId("conv1", PageRequest.of(0, 10));

        assertThat(messages).hasSize(2);
    }

    @Test
    void testFindByConversationIdAndTimestampBefore() {
        Instant now = Instant.now();

        ChatMessage msg1 = new ChatMessage();
        msg1.setConversationId("conv2");
        msg1.setMessageType(MessageType.CHAT);
        msg1.setContent("hello 1");
        msg1.setTimestamp(now.minusSeconds(600));
        mongoTemplate.save(msg1);

        ChatMessage msg2 = new ChatMessage();
        msg2.setConversationId("conv2");
        msg2.setMessageType(MessageType.CHAT);
        msg2.setContent("hello 2");
        msg2.setTimestamp(now.minusSeconds(300));
        mongoTemplate.save(msg2);

        List<ChatMessage> messages = messageRepository.findByConversationIdAndTimestampBefore("conv2",
                now.minusSeconds(120), PageRequest.of(0, 10));

        assertThat(messages).hasSize(2);
    }

    @Test
    void testCountUnreadMessagesBySender_WithWatermark() {
        Instant t1 = Instant.parse("2026-09-17T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-17T10:05:00Z");
        Instant t3 = Instant.parse("2026-09-17T10:10:00Z");

        // Alice sends msg1 at t1 to Bob (conversation alice_bob)
        ChatMessage msg1 = new ChatMessage();
        msg1.setConversationId("alice_bob");
        msg1.setSender("alice");
        msg1.setRecipient("bob");
        msg1.setMessageType(MessageType.CHAT);
        msg1.setContent("msg1");
        msg1.setTimestamp(t1);
        msg1.setDeleted(false);
        mongoTemplate.save(msg1);

        // Without any ReadReceipt, Bob should see 1 unread message from Alice
        List<UnreadCountProjection> countsBeforeReceipt = messageRepository.countUnreadMessagesBySender("bob");
        assertThat(countsBeforeReceipt).hasSize(1);
        assertThat(countsBeforeReceipt.get(0).sender()).isEqualTo("alice");
        assertThat(countsBeforeReceipt.get(0).count()).isEqualTo(1L);

        // Bob marks as read at t2
        ReadReceipt receipt = ReadReceipt.builder()
                .id("alice_bob:bob")
                .conversationId("alice_bob")
                .username("bob")
                .lastReadTimestamp(t2)
                .build();
        mongoTemplate.save(receipt);

        // Now msg1 (at t1 <= t2) is considered read
        List<UnreadCountProjection> countsAfterRead = messageRepository.countUnreadMessagesBySender("bob");
        assertThat(countsAfterRead).isEmpty();

        // Alice sends msg2 at t3 (> t2) to Bob
        ChatMessage msg2 = new ChatMessage();
        msg2.setConversationId("alice_bob");
        msg2.setSender("alice");
        msg2.setRecipient("bob");
        msg2.setMessageType(MessageType.CHAT);
        msg2.setContent("msg2");
        msg2.setTimestamp(t3);
        msg2.setDeleted(false);
        mongoTemplate.save(msg2);

        List<UnreadCountProjection> countsNewMsg = messageRepository.countUnreadMessagesBySender("bob");
        assertThat(countsNewMsg).hasSize(1);
        assertThat(countsNewMsg.get(0).count()).isEqualTo(1L);

        // Alice revokes msg2 (isDeleted = true)
        msg2.setDeleted(true);
        mongoTemplate.save(msg2);

        // Naturally excluded from unread count without mutating any counter!
        List<UnreadCountProjection> countsAfterRevoke = messageRepository.countUnreadMessagesBySender("bob");
        assertThat(countsAfterRevoke).isEmpty();
    }
}
