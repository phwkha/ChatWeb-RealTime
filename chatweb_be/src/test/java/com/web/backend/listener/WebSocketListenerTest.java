package com.web.backend.listener;

import com.web.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketListenerTest {

    @Mock
    private UserService userService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private WebSocketListener webSocketListener;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    private Message<byte[]> createStompMessage(String username) {
        Map<String, Object> headers = new HashMap<>();
        if (username != null) {
            Principal principal = () -> username;
            headers.put("simpUser", principal);
        }
        return new GenericMessage<>(new byte[0], headers);
    }

    @Test
    void testHandleWebSocketConnectListener_InitialSession_CancelsDebounceAndMarksOnline() {
        String username = "alice";
        Message<byte[]> message = createStompMessage(username);
        SessionConnectedEvent event = new SessionConnectedEvent(this, message);

        when(hashOperations.increment(eq("online_users_count"), eq(username), eq(1L))).thenReturn(1L);

        webSocketListener.handleWebSocketConnectListener(event);

        verify(zSetOperations).remove("presence:offline_queue", username);
        verify(zSetOperations).add(eq("online_users"), eq(username), anyDouble());
        verify(userService).setUserOnlineStatus(username, true);
        verify(hashOperations).increment(eq("ws:routing:servers:" + username), anyString(), eq(1L));
    }

    @Test
    void testHandleWebSocketConnectListener_AdditionalSession_DoesNotMarkOnlineAgain() {
        String username = "bob";
        Message<byte[]> message = createStompMessage(username);
        SessionConnectedEvent event = new SessionConnectedEvent(this, message);

        when(hashOperations.increment(eq("online_users_count"), eq(username), eq(1L))).thenReturn(2L);

        webSocketListener.handleWebSocketConnectListener(event);

        verify(zSetOperations).remove("presence:offline_queue", username);
        verify(zSetOperations).add(eq("online_users"), eq(username), anyDouble());
        verify(userService, never()).setUserOnlineStatus(username, true);
    }

    @Test
    void testHandleWebSocketDisconnectListener_LastSession_QueuesDistributedDebounce() {
        String username = "charlie";
        Message<byte[]> message = createStompMessage(username);
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-123", org.springframework.web.socket.CloseStatus.NORMAL);

        when(hashOperations.increment(eq("online_users_count"), eq(username), eq(-1L))).thenReturn(0L);

        webSocketListener.handleWebSocketDisconnectListener(event);

        verify(zSetOperations).add(eq("presence:offline_queue"), eq(username), anyDouble());
        verify(userService, never()).setUserOnlineStatus(eq(username), anyBoolean());
    }

    @Test
    void testHandleWebSocketDisconnectListener_RemainingSessions_DoesNotQueueDebounce() {
        String username = "david";
        Message<byte[]> message = createStompMessage(username);
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-456", org.springframework.web.socket.CloseStatus.NORMAL);

        when(hashOperations.increment(eq("online_users_count"), eq(username), eq(-1L))).thenReturn(1L);

        webSocketListener.handleWebSocketDisconnectListener(event);

        verify(zSetOperations, never()).add(eq("presence:offline_queue"), anyString(), anyDouble());
        verify(userService, never()).setUserOnlineStatus(eq(username), anyBoolean());
    }

    @Test
    void testHandleWebSocketListener_NullUser_Ignored() {
        Message<byte[]> message = createStompMessage(null);
        SessionConnectedEvent connectEvent = new SessionConnectedEvent(this, message);
        SessionDisconnectEvent disconnectEvent = new SessionDisconnectEvent(this, message, "s1", org.springframework.web.socket.CloseStatus.NORMAL);

        webSocketListener.handleWebSocketConnectListener(connectEvent);
        webSocketListener.handleWebSocketDisconnectListener(disconnectEvent);

        verifyNoInteractions(userService);
    }
}
