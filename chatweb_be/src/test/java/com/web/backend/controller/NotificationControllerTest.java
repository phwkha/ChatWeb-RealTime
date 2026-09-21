package com.web.backend.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.CursorResponse;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.jwt.JwtAuthenticationFilter;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.JwtService;
import com.web.backend.service.NotificationService;
import com.web.backend.service.UserServiceDetail;
import com.web.backend.service.WebSocketRoutingService;

@ActiveProfiles("test")
@WebMvcTest(controllers = NotificationController.class, excludeAutoConfiguration = {
		SecurityAutoConfiguration.class,
		SecurityFilterAutoConfiguration.class,
		OAuth2ClientAutoConfiguration.class,
		OAuth2ClientWebSecurityAutoConfiguration.class,
		OAuth2ResourceServerAutoConfiguration.class
}, excludeFilters = {
		@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationService notificationService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private UserServiceDetail userServiceDetail;

	@MockitoBean
	private RedisTemplate<String, Object> redisTemplate;

	@MockitoBean
	private SimpMessagingTemplate simpMessagingTemplate;

	@MockitoBean
	private WebSocketRoutingService webSocketRoutingService;

	private UsernamePasswordAuthenticationToken mockAuth;
	private UserEntity mockUser;

	@BeforeEach
	void setUp() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("i18n/messages");
		messageSource.setDefaultEncoding("UTF-8");
		Translator.setStaticMessageSource(messageSource);

		mockUser = new UserEntity();
		mockUser.setUsername("testuser");

		mockAuth = new UsernamePasswordAuthenticationToken(mockUser, null, Collections.emptyList());
	}

	@Test
	void testGetNotifications_DefaultParams_Success() throws Exception {
		NotificationResponse notification = NotificationResponse.builder()
				.id(1L)
				.content("You received a friend request")
				.isRead(false)
				.build();

		CursorResponse<NotificationResponse> cursorResponse = new CursorResponse<>(
				List.of(notification),
				"cursor123",
				true);

		when(notificationService.getNotifications(mockUser, null, 20))
				.thenReturn(cursorResponse);

		mockMvc.perform(get("/api/notifications")
				.principal(mockAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content[0].id").value(1))
				.andExpect(jsonPath("$.data.content[0].content").value("You received a friend request"))
				.andExpect(jsonPath("$.data.nextCursor").value("cursor123"))
				.andExpect(jsonPath("$.data.hasMore").value(true));

		verify(notificationService).getNotifications(mockUser, null, 20);
	}

	@Test
	void testGetNotifications_WithCursorAndSize_Success() throws Exception {
		CursorResponse<NotificationResponse> cursorResponse = new CursorResponse<>(
				Collections.emptyList(),
				null,
				false);

		when(notificationService.getNotifications(mockUser, "cursor456", 50))
				.thenReturn(cursorResponse);

		mockMvc.perform(get("/api/notifications")
				.principal(mockAuth)
				.param("cursor", "cursor456")
				.param("size", "50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data.content").isEmpty())
				.andExpect(jsonPath("$.data.hasMore").value(false));

		verify(notificationService).getNotifications(mockUser, "cursor456", 50);
	}

	@Test
	void testGetNotifications_InvalidSizeMin_BadRequest() throws Exception {
		mockMvc.perform(get("/api/notifications")
				.principal(mockAuth)
				.param("size", "0"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testGetNotifications_InvalidSizeMax_BadRequest() throws Exception {
		mockMvc.perform(get("/api/notifications")
				.principal(mockAuth)
				.param("size", "101"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testGetUnreadNotificationCounts_Success() throws Exception {
		when(notificationService.getUnreadNotificationCounts(mockUser))
				.thenReturn(5L);

		mockMvc.perform(get("/api/notifications/unread-counts")
				.principal(mockAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data").value(5));

		verify(notificationService).getUnreadNotificationCounts(mockUser);
	}

	@Test
	void testMarkNotificationAsRead_Success() throws Exception {
		mockMvc.perform(patch("/api/notifications/10/read")
				.principal(mockAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200));

		verify(notificationService).markNotificationAsRead(mockUser, 10L);
	}

	@Test
	void testMarkNotificationAsRead_InvalidId_BadRequest() throws Exception {
		mockMvc.perform(patch("/api/notifications/0/read")
				.principal(mockAuth))
				.andExpect(status().isBadRequest());

		mockMvc.perform(patch("/api/notifications/-5/read")
				.principal(mockAuth))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testMarkAllAsRead_Success() throws Exception {
		when(notificationService.markAllNotificationsAsRead(mockUser))
				.thenReturn(7);

		mockMvc.perform(patch("/api/notifications/read-all")
				.principal(mockAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(200))
				.andExpect(jsonPath("$.data").value(7));

		verify(notificationService).markAllNotificationsAsRead(mockUser);
	}
}
