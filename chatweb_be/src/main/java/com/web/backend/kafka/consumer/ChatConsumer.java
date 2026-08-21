package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.kafka.avro.ChatMessageAvro;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.model.SystemMessage;
import com.web.backend.service.WebSocketRoutingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "CHAT-KAFKA-CONSUMER")
public class ChatConsumer {

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final MessageMapper messageMapper;

    private final WebSocketRoutingService webSocketRoutingService;

    private static final String QUEUE_MESSAGES_STRING = "/queue/messages";

    private static final String TOPIC_PUBLIC_STRING = "/topic/public";

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 200), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-group-id}", containerFactory = "chatAvroListenerContainerFactory")
    public void listenChatMessages(
            @Payload ChatMessageAvro message,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String conversationKey) {
        if (message == null) {
            return;
        }
        String recipient = message.getRecipient();
        String sender = message.getSender();
        log.debug("Consumed chat message (Avro) [key='{}']: sender='{}', recipient='{}'", conversationKey, sender,
                recipient);
        try {
            ChatMessageResponse messageResponse = messageMapper.avroToResponse(message);
            webSocketRoutingService.routeMessage(recipient, QUEUE_MESSAGES_STRING, messageResponse);
            webSocketRoutingService.routeMessage(sender, QUEUE_MESSAGES_STRING, messageResponse);
            log.debug("Dispatched chat message to WebSocket sender '{}' and recipient '{}'", sender, recipient);
        } catch (Exception e) {
            log.error("Failed to route WebSocket chat message for sender '{}' and recipient '{}'", sender, recipient,
                    e);
            throw new RuntimeException(e);
        }
    }

    @DltHandler
    public void handleChatDlt(ChatMessageAvro message) {
        log.error("Dead Letter Topic: Failed to deliver real-time WebSocket chat from '{}' to '{}'",
                message.getSender(), message.getRecipient());
    }

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 200), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.chat.system-messages}", groupId = "${spring.kafka.topic.chat.system-messages-group-id}-${random.uuid}", containerFactory = "jsonKafkaListenerContainerFactory")
    public void listenSystemMessages(SystemMessage systemMessage) {
        if (systemMessage == null)
            return;

        MessageSystemResponse response = messageMapper.systemMessageToResponse(systemMessage);

        log.debug("Consumed system message from sender '{}'", response.getSender());

        try {
            simpMessagingTemplate.convertAndSend(TOPIC_PUBLIC_STRING, response);
            log.debug("Broadcasted system message to topic '{}'", TOPIC_PUBLIC_STRING);
        } catch (Exception e) {
            log.error("Failed to broadcast system message to '{}'", TOPIC_PUBLIC_STRING, e);
            throw new RuntimeException(e);
        }
    }

    @DltHandler
    public void handleSystemMessageDlt(SystemMessage systemMessage) {
        log.error("Dead Letter Topic: Failed to broadcast system message from '{}'",
                systemMessage.getSender());
    }
}