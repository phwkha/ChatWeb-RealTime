package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import com.web.backend.controller.response.ChatMessageResponse;
import com.web.backend.controller.response.MessageSystemResponse;
import com.web.backend.mapper.MessageMapper;
import com.web.backend.kafka.payload.ChatMessagePayload;
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

    @KafkaListener(topics = "${spring.kafka.topic.chat.messages}", groupId = "${spring.kafka.topic.chat.messages-group-id}")
    public void listenChatMessages(ChatMessagePayload message) {
        if (message == null) {
            return;
        }
        String recipient = message.getRecipient();
        String sender = message.getSender();
        log.debug("Consumed chat message: sender='{}', recipient='{}'", sender, recipient);
        try {
            ChatMessageResponse messageResponse = messageMapper.payloadToResponse(message);

            webSocketRoutingService.routeMessage(recipient, QUEUE_MESSAGES_STRING, messageResponse);

            log.debug("Dispatched chat message to WebSocket recipient '{}'", recipient);
        } catch (Exception e) {
            log.error("Failed to route WebSocket chat message to recipient '{}'", recipient, e);
        }
    }

    @KafkaListener(topics = "${spring.kafka.topic.chat.system-messages}", groupId = "${spring.kafka.topic.chat.system-messages-group-id}-${random.uuid}")
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
        }
    }
}