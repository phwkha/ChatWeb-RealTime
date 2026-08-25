package com.web.backend.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.model.mongodb.SystemMessage;

@Component
@Slf4j(topic = "CHAT-KAFKA-PRODUCER")
@RequiredArgsConstructor
public class ChatProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTemplate<String, ChatMessageAvro> avroChatKafkaTemplate;

    @Value("${spring.kafka.topic.chat.messages}")
    private String chatTopic;

    @Value("${spring.kafka.topic.chat.system-messages}")
    private String systemTopic;

    public CompletableFuture<SendResult<String, ChatMessageAvro>> sendChatMessage(ChatMessageAvro messageChat) {
        CompletableFuture<SendResult<String, ChatMessageAvro>> future = avroChatKafkaTemplate.send(
                Objects.requireNonNull(chatTopic),
                messageChat.getConversationId(),
                messageChat);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish Chat Message (Avro) to Kafka topic '{}'", chatTopic, ex);
            } else {
                log.debug("Published Chat Message (Avro) to Kafka topic '{}' [partition={}, offset={}]",
                        chatTopic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
        return future;
    }

    public CompletableFuture<SendResult<String, Object>> sendSystemMessage(SystemMessage messageSystem) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                Objects.requireNonNull(systemTopic), messageSystem);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish messageSystem to Kafka topic '{}'", systemTopic, ex);
            } else {
                log.debug("Published messageSystem to Kafka topic '{}' [offset={}]", systemTopic,
                        result.getRecordMetadata().offset());
            }
        });
        return future;
    }

}
