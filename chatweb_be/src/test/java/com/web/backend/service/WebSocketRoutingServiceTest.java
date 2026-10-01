package com.web.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.web.backend.config.ServerIdentity;
import com.web.backend.model.redis.RedisWsMessage;

@ExtendWith(MockitoExtension.class)
class WebSocketRoutingServiceTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private WebSocketRoutingService webSocketRoutingService;

    @Test
    void testRouteMessage_NullUsername_DoesNothing() throws Exception {
        webSocketRoutingService.routeMessage(null, "/queue/messages", "payload");
        verify(redisTemplate, never()).opsForHash();
        verify(simpMessagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void testRouteMessage_UserOffline_DoesNothing() throws Exception {
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.keys("ws:routing:servers:alice")).thenReturn(Collections.emptySet());

        webSocketRoutingService.routeMessage("alice", "/queue/messages", "payload");

        verify(simpMessagingTemplate, never()).convertAndSendToUser(any(), any(), any());
        verify(redisTemplate, never()).convertAndSend(any(), any());
    }

    @Test
    void testRouteMessage_LocalServer_SendsLocally() throws Exception {
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.keys("ws:routing:servers:alice")).thenReturn(Set.of(ServerIdentity.SERVER_ID));

        webSocketRoutingService.routeMessage("alice", "/queue/messages", "localPayload");

        verify(simpMessagingTemplate).convertAndSendToUser("alice", "/queue/messages", "localPayload");
        verify(redisTemplate, never()).convertAndSend(any(), any());
    }

    @Test
    void testRouteMessage_RemoteServer_PublishesToRedis() throws Exception {
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.keys("ws:routing:servers:alice")).thenReturn(Set.of("remote-node-2"));

        webSocketRoutingService.routeMessage("alice", "/queue/messages", "remotePayload");

        verify(simpMessagingTemplate, never()).convertAndSendToUser(any(), any(), any());
        verify(redisTemplate).convertAndSend(eq("channel:server:remote-node-2"), any(RedisWsMessage.class));
    }

    @Test
    void testRouteMessageToSession_NullSessionId_DoesNothing() {
        webSocketRoutingService.routeMessageToSession(null, "/queue/errors", "errorPayload");
        verify(simpMessagingTemplate, never()).convertAndSendToUser(any(), any(), any(), any(MessageHeaders.class));
    }

    @Test
    void testRouteMessageToSession_ValidSession_Sends() {
        webSocketRoutingService.routeMessageToSession("session-123", "/queue/errors", "errorPayload");
        verify(simpMessagingTemplate).convertAndSendToUser(eq("session-123"), eq("/queue/errors"), eq("errorPayload"), any(MessageHeaders.class));
    }
}
