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

    private static final String WS_ROUTING_SERVERS_KEY = "ws:routing:servers:";
    private static final String CHANNEL_SERVER_STRING = "channel:server:";

    public void routeMessage(String username, String destination, Object payload) throws Exception {
        if (username == null) {
            return;
        }
        java.util.Set<Object> targetServerIds = redisTemplate.opsForHash().keys(WS_ROUTING_SERVERS_KEY + username);
        if (targetServerIds != null && !targetServerIds.isEmpty()) {
            boolean sentLocally = false;
            for (Object serverIdObj : targetServerIds) {
                String targetServerId = serverIdObj.toString();
                if (ServerIdentity.SERVER_ID.equals(targetServerId)) {
                    if (!sentLocally) {
                        simpMessagingTemplate.convertAndSendToUser(username, destination, payload);
                        log.debug("Routed message locally to user '{}' on destination '{}'", username, destination);
                        sentLocally = true;
                    }
                } else {
                    RedisWsMessage wsMessage = new RedisWsMessage(username, destination, payload);
                    redisTemplate.convertAndSend(CHANNEL_SERVER_STRING + targetServerId, wsMessage);
                    log.debug("Routed message to target server '{}' for user '{}' on destination '{}'", targetServerId,
                            username, destination);
                }
            }
        } else {
            log.debug("User '{}' is offline. Skipped WebSocket routing on destination '{}'", username, destination);
        }
    }
}
