package com.web.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import com.web.backend.kafka.avro.ChatMessageAvro;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @MockitoBean
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @MockitoBean
    private org.springframework.kafka.core.KafkaAdmin kafkaAdmin;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private KafkaTemplate<String, ChatMessageAvro> avroChatKafkaTemplate;

    @Test
    void contextLoads() {
    }

}
