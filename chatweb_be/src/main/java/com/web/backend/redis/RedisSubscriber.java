package com.web.backend.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisSubscriber {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public void receiveMessage(RedisWsMessage wsMessage) {
        try {
            simpMessagingTemplate.convertAndSendToUser(wsMessage.getRecipient(), wsMessage.getDestination(),
                    wsMessage.getPayload());
            log.info("Routed WebSocket message to {} via Redis Pub/Sub", wsMessage.getRecipient());
        } catch (Exception e) {
            log.error("Error processing Redis WebSocket message: {}", e.getMessage(), e);
        }
    }
}

