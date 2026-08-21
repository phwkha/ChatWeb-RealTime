package com.web.backend.repository;

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

import com.web.backend.model.mongo.SystemMessage;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataMongoTest
class SystemMessageRepositoryTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0").withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SystemMessageRepository systemMessageRepository;

    @AfterEach
    void cleanUp() {
        systemMessageRepository.deleteAll();
    }

    @Test
    void testFindInitialMessage() {
        SystemMessage msg1 = new SystemMessage();
        msg1.setSender("sys1");
        msg1.setContent("hello 1");
        msg1.setTimestamp(Instant.now());
        mongoTemplate.save(msg1);

        SystemMessage msg2 = new SystemMessage();
        msg2.setSender("sys2");
        msg2.setContent("hello 2");
        msg2.setTimestamp(Instant.now().minusSeconds(60));
        mongoTemplate.save(msg2);

        List<SystemMessage> messages = systemMessageRepository.findInitialMessage(PageRequest.of(0, 10));

        assertThat(messages).isNotEmpty();
    }

    @Test
    void testFindMessage_Success() {
        SystemMessage msg1 = new SystemMessage();
        msg1.setSender("sys1");
        msg1.setContent("hello 1");
        Instant now = Instant.now();
        msg1.setTimestamp(now.minusSeconds(120));
        mongoTemplate.save(msg1);

        SystemMessage msg2 = new SystemMessage();
        msg2.setSender("sys2");
        msg2.setContent("hello 2");
        msg2.setTimestamp(now.minusSeconds(60));
        mongoTemplate.save(msg2);

        List<SystemMessage> messages = systemMessageRepository.findMessage(now.minusSeconds(30), PageRequest.of(0, 10));

        assertThat(messages).hasSize(2);
    }
}
