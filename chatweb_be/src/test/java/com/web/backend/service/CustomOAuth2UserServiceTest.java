package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestOperations;

import com.web.backend.common.AuthProvider;
import com.web.backend.common.UserStatus;
import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;
import com.web.backend.oauth2.CustomOAuth2User;
import com.web.backend.repository.RoleRepository;
import com.web.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private RestOperations restOperations;

	private CustomOAuth2UserService customOAuth2UserService;

	@BeforeEach
	void setUp() {
		customOAuth2UserService = new CustomOAuth2UserService(userRepository, roleRepository, passwordEncoder);
		customOAuth2UserService.setRestOperations(restOperations);
	}

	private OAuth2UserRequest createOAuth2UserRequest(String registrationId, String userNameAttributeName) {
		ClientRegistration clientRegistration = ClientRegistration.withRegistrationId(registrationId)
				.clientId("client-" + registrationId)
				.clientSecret("client-secret")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("http://localhost:8080/login/oauth2/code/" + registrationId)
				.authorizationUri("https://example.com/oauth2/authorize")
				.tokenUri("https://example.com/oauth2/token")
				.userInfoUri("https://example.com/userinfo")
				.userNameAttributeName(userNameAttributeName)
				.clientName(registrationId)
				.build();

		OAuth2AccessToken accessToken = new OAuth2AccessToken(
				OAuth2AccessToken.TokenType.BEARER,
				"mock-access-token",
				Instant.now(),
				Instant.now().plusSeconds(3600));

		return new OAuth2UserRequest(clientRegistration, accessToken);
	}

	private void mockUserInfoResponse(Map<String, Object> attributes) {
		ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(attributes, HttpStatus.OK);
		when(restOperations.exchange(
				ArgumentMatchers.<RequestEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(responseEntity);
	}

	@Test
	void testLoadUser_ExistingUserByProviderId_ReturnsOAuth2User() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("google", "sub");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "google-sub-123");
		attributes.put("email", "existing@gmail.com");
		attributes.put("name", "Existing User");

		mockUserInfoResponse(attributes);

		UserEntity existingUser = new UserEntity();
		existingUser.setUsername("existinguser");
		existingUser.setEmail("existing@gmail.com");
		existingUser.setProviderId("google-sub-123");

		when(userRepository.findByProviderId("google-sub-123")).thenReturn(Optional.of(existingUser));

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		assertNotNull(result);
		assertInstanceOf(CustomOAuth2User.class, result);
		CustomOAuth2User customOAuth2User = (CustomOAuth2User) result;
		assertEquals(existingUser, customOAuth2User.getUserEntity());
		assertEquals("existing@gmail.com", customOAuth2User.getName());

		verify(userRepository, never()).save(any());
	}

	@Test
	void testLoadUser_NewUser_Success() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("google", "sub");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "google-sub-456");
		attributes.put("email", "newuser@gmail.com");
		attributes.put("name", "New User");
		attributes.put("given_name", "New");
		attributes.put("family_name", "User");
		attributes.put("picture", "https://example.com/pic.jpg");

		mockUserInfoResponse(attributes);

		when(userRepository.findByProviderId("google-sub-456")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.empty());

		RoleEntity role = new RoleEntity();
		role.setName("USER");
		when(roleRepository.findByNameOauth2("USER")).thenReturn(Optional.of(role));
		when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");

		UserEntity savedUser = new UserEntity();
		savedUser.setUsername("NewUser_123");
		savedUser.setEmail("newuser@gmail.com");
		savedUser.setProviderId("google-sub-456");
		savedUser.setRole(role);
		savedUser.setAuthProvider(AuthProvider.GOOGLE);
		savedUser.setUserStatus(UserStatus.ACTIVE);

		when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		assertNotNull(result);
		assertInstanceOf(CustomOAuth2User.class, result);
		CustomOAuth2User customOAuth2User = (CustomOAuth2User) result;
		assertEquals(savedUser, customOAuth2User.getUserEntity());

		ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
		verify(userRepository).save(userCaptor.capture());
		UserEntity capturedUser = userCaptor.getValue();
		assertEquals("newuser@gmail.com", capturedUser.getEmail());
		assertEquals("google-sub-456", capturedUser.getProviderId());
		assertEquals(AuthProvider.GOOGLE, capturedUser.getAuthProvider());
		assertEquals(UserStatus.ACTIVE, capturedUser.getUserStatus());
		assertEquals("New", capturedUser.getFirstName());
		assertEquals("User", capturedUser.getLastName());
		assertEquals("https://example.com/pic.jpg", capturedUser.getAvatar());
		assertTrue(capturedUser.getUsername().startsWith("NewUser_"));
	}

	@Test
	void testLoadUser_NewUserWithoutName_UsesDefaultPrefix() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("google", "sub");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "google-sub-789");
		attributes.put("email", "noname@gmail.com");

		mockUserInfoResponse(attributes);

		when(userRepository.findByProviderId("google-sub-789")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("noname@gmail.com")).thenReturn(Optional.empty());

		RoleEntity role = new RoleEntity();
		role.setName("USER");
		when(roleRepository.findByNameOauth2("USER")).thenReturn(Optional.of(role));
		when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");

		when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		assertNotNull(result);
		ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
		verify(userRepository).save(userCaptor.capture());
		assertTrue(userCaptor.getValue().getUsername().startsWith("user_"));
	}

	@Test
	void testLoadUser_ConflictByEmail_ThrowsOAuth2AuthenticationException() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("google", "sub");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "google-sub-999");
		attributes.put("email", "conflict@gmail.com");

		mockUserInfoResponse(attributes);

		when(userRepository.findByProviderId("google-sub-999")).thenReturn(Optional.empty());
		UserEntity conflictUser = new UserEntity();
		conflictUser.setEmail("conflict@gmail.com");
		when(userRepository.findByEmail("conflict@gmail.com")).thenReturn(Optional.of(conflictUser));

		OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class,
				() -> customOAuth2UserService.loadUser(userRequest));

		assertEquals("error.oauth2.email_already_exists", ex.getError().getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	void testLoadUser_EmailMissing_ThrowsOAuth2AuthenticationException() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("google", "sub");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "google-sub-no-email");

		mockUserInfoResponse(attributes);

		OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class,
				() -> customOAuth2UserService.loadUser(userRequest));

		assertEquals("error.oauth2.email_missing", ex.getError().getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	void testLoadUser_EmailEmpty_ThrowsOAuth2AuthenticationException() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("google", "sub");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "google-sub-empty-email");
		attributes.put("email", "");

		mockUserInfoResponse(attributes);

		OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class,
				() -> customOAuth2UserService.loadUser(userRequest));

		assertEquals("error.oauth2.email_missing", ex.getError().getErrorCode());
	}

	@Test
	void testLoadUser_RoleNotFound_ThrowsOAuth2AuthenticationException() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("google", "sub");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "google-sub-norole");
		attributes.put("email", "norole@gmail.com");

		mockUserInfoResponse(attributes);

		when(userRepository.findByProviderId("google-sub-norole")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("norole@gmail.com")).thenReturn(Optional.empty());
		when(roleRepository.findByNameOauth2("USER")).thenReturn(Optional.empty());

		OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class,
				() -> customOAuth2UserService.loadUser(userRequest));

		assertEquals("error.role.not_found", ex.getError().getErrorCode());
		verify(userRepository, never()).save(any());
	}

	@Test
	void testLoadUser_GithubProviderId_ExtractedFromIdAttribute() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("github", "id");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("id", 12345678);
		attributes.put("email", "github@gmail.com");
		attributes.put("name", "GithubUser");

		mockUserInfoResponse(attributes);

		when(userRepository.findByProviderId("12345678")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("github@gmail.com")).thenReturn(Optional.empty());

		RoleEntity role = new RoleEntity();
		role.setName("USER");
		when(roleRepository.findByNameOauth2("USER")).thenReturn(Optional.of(role));
		when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
		when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		assertNotNull(result);
		ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
		verify(userRepository).save(userCaptor.capture());
		assertEquals("12345678", userCaptor.getValue().getProviderId());
		assertEquals(AuthProvider.GITHUB, userCaptor.getValue().getAuthProvider());
	}

	@Test
	void testLoadUser_FacebookProviderId_ExtractedFromIdAttribute() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("facebook", "id");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("id", "fb-999888");
		attributes.put("email", "fb@facebook.com");
		attributes.put("name", "FbUser");

		mockUserInfoResponse(attributes);

		when(userRepository.findByProviderId("fb-999888")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("fb@facebook.com")).thenReturn(Optional.empty());

		RoleEntity role = new RoleEntity();
		role.setName("USER");
		when(roleRepository.findByNameOauth2("USER")).thenReturn(Optional.of(role));
		when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
		when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		assertNotNull(result);
		ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
		verify(userRepository).save(userCaptor.capture());
		assertEquals("fb-999888", userCaptor.getValue().getProviderId());
		assertEquals(AuthProvider.FACEBOOK, userCaptor.getValue().getAuthProvider());
	}

	@Test
	void testLoadUser_UnknownAuthProvider_FallsBackToGoogle() {
		OAuth2UserRequest userRequest = createOAuth2UserRequest("customprovider", "sub");

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "cust-111");
		attributes.put("email", "custom@example.com");

		mockUserInfoResponse(attributes);

		when(userRepository.findByProviderId("cust-111")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("custom@example.com")).thenReturn(Optional.empty());

		RoleEntity role = new RoleEntity();
		role.setName("USER");
		when(roleRepository.findByNameOauth2("USER")).thenReturn(Optional.of(role));
		when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
		when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OAuth2User result = customOAuth2UserService.loadUser(userRequest);

		assertNotNull(result);
		ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
		verify(userRepository).save(userCaptor.capture());
		assertEquals(AuthProvider.GOOGLE, userCaptor.getValue().getAuthProvider());
	}
}
