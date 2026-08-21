package com.web.backend.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.web.backend.model.redis.RedisWsMessage;

@Component
@Slf4j(topic = "REDIS-SUBSCRIBER")
@RequiredArgsConstructor
public class RedisSubscriber {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void receiveMessage(RedisWsMessage wsMessage) {
        try {
            simpMessagingTemplate.convertAndSendToUser(wsMessage.getRecipient(), wsMessage.getDestination(),
                    wsMessage.getPayload());
            log.debug("Routed WebSocket message to '{}' on destination '{}' via Redis Pub/Sub",
                    wsMessage.getRecipient(), wsMessage.getDestination());
        } catch (Exception e) {
            log.error("Failed to process Redis WebSocket message for recipient '{}'", wsMessage.getRecipient(), e);
        }
    }
}
