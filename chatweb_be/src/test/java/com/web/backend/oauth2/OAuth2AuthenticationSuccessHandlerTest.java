package com.web.backend.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.service.JwtService;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

	@Mock
	private JwtService jwtService;

	@Mock
	private Authentication authentication;

	private OAuth2AuthenticationSuccessHandler successHandler;

	private static final String REDIRECT_URI = "http://localhost:3000/oauth2/redirect";

	@BeforeEach
	void setUp() {
		successHandler = new OAuth2AuthenticationSuccessHandler(jwtService);
		ReflectionTestUtils.setField(successHandler, "redirectUri", REDIRECT_URI);
	}

	@Test
	void testOnAuthenticationSuccess_SetsCookiesAndRedirects() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		com.web.backend.model.postgres.PermissionEntity permission = new com.web.backend.model.postgres.PermissionEntity();
		permission.setName("ROLE_USER");

		RoleEntity role = new RoleEntity();
		role.setName("USER");
		role.setPermissions(java.util.Set.of(permission));

		UserEntity user = new UserEntity();
		user.setUsername("testuser");
		user.setEmail("test@example.com");
		user.setTokenVersion(1);
		user.setRole(role);

		CustomOAuth2User customOAuth2User = new CustomOAuth2User(user, Map.of("email", "test@example.com"));

		when(authentication.getPrincipal()).thenReturn(customOAuth2User);
		when(jwtService.generateAccessToken("testuser", List.of("ROLE_USER"), 1))
				.thenReturn("mock-access-token");
		when(jwtService.generateRefreshToken("testuser", 1))
				.thenReturn("mock-refresh-token");

		successHandler.onAuthenticationSuccess(request, response, authentication);

		Collection<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
		assertNotNull(cookies);
		assertEquals(2, cookies.size());

		boolean hasAccessToken = false;
		boolean hasRefreshToken = false;

		for (String cookie : cookies) {
			if (cookie.contains("accessToken=mock-access-token")) {
				hasAccessToken = true;
				assertTrue(cookie.contains("Path=/;"));
				assertTrue(cookie.contains("HttpOnly"));
				assertTrue(cookie.contains("Secure"));
				assertTrue(cookie.contains("SameSite=Strict"));
			}
			if (cookie.contains("refreshToken=mock-refresh-token")) {
				hasRefreshToken = true;
				assertTrue(cookie.contains("Path=/api/auth"));
				assertTrue(cookie.contains("HttpOnly"));
				assertTrue(cookie.contains("Secure"));
				assertTrue(cookie.contains("SameSite=Strict"));
			}
		}

		assertTrue(hasAccessToken, "Should have accessToken cookie");
		assertTrue(hasRefreshToken, "Should have refreshToken cookie");
		assertEquals(REDIRECT_URI, response.getRedirectedUrl());

		verify(jwtService).generateAccessToken("testuser", List.of("ROLE_USER"), 1);
		verify(jwtService).generateRefreshToken("testuser", 1);
	}
}
