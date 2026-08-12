package com.web.backend.service;

import com.web.backend.config.ServerIdentity;
import com.web.backend.redis.RedisWsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "WS-ROUTING-SERVICE")
public class WebSocketRoutingService {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String WS_ROUTING_STRING = "ws:routing:";
    private static final String CHANNEL_SERVER_STRING = "channel:server:";

    public void routeMessage(String username, String destination, Object payload) throws Exception {
        if (username == null) {
            return;
        }
        String targetServerId = (String) redisTemplate.opsForValue().get(WS_ROUTING_STRING + username);
        if (targetServerId != null) {
            if (ServerIdentity.SERVER_ID.equals(targetServerId)) {
                simpMessagingTemplate.convertAndSendToUser(username, destination, payload);
                log.info("Sent locally to {}", username);
            } else {
                RedisWsMessage wsMessage = new RedisWsMessage(username, destination, payload);
                redisTemplate.convertAndSend(CHANNEL_SERVER_STRING + targetServerId, wsMessage);
                log.info("Routed to Server {} for user {}", targetServerId, username);
            }
        } else {
            log.info("User {} is offline, skipped routing.", username);
        }
    }
}
