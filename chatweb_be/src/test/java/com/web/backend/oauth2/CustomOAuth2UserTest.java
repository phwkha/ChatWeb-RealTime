package com.web.backend.oauth2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.web.backend.model.postgres.RoleEntity;
import com.web.backend.model.postgres.UserEntity;

class CustomOAuth2UserTest {

	@Test
	void testConstructor_WithUserEntityAndAttributes() {
		com.web.backend.model.postgres.PermissionEntity permission = new com.web.backend.model.postgres.PermissionEntity();
		permission.setName("ROLE_USER");

		RoleEntity role = new RoleEntity();
		role.setName("USER");
		role.setPermissions(java.util.Set.of(permission));

		UserEntity user = new UserEntity();
		user.setUsername("testuser");
		user.setEmail("test@example.com");
		user.setRole(role);

		Map<String, Object> attributes = Map.of(
				"sub", "12345",
				"email", "test@example.com",
				"name", "Test User");

		CustomOAuth2User oauthUser = new CustomOAuth2User(user, attributes);

		assertEquals(user, oauthUser.getUserEntity());
		assertEquals(attributes, oauthUser.getAttributes());
		assertEquals("test@example.com", oauthUser.getName());
		assertNotNull(oauthUser.getAuthorities());
		assertFalse(oauthUser.getAuthorities().isEmpty());
	}

	@Test
	void testConstructor_WithNullUserEntity() {
		Map<String, Object> attributes = Map.of("email", "test@example.com");
		CustomOAuth2User oauthUser = new CustomOAuth2User(null, attributes);

		assertNull(oauthUser.getUserEntity());
		assertEquals(attributes, oauthUser.getAttributes());
		assertNull(oauthUser.getName());
		assertNotNull(oauthUser.getAuthorities());
		assertTrue(oauthUser.getAuthorities().isEmpty());
	}

	@Test
	void testConstructor_WithUserEntityHavingNullAuthorities() {
		UserEntity user = new UserEntity();
		user.setEmail("test@example.com");
		user.setRole(null);

		CustomOAuth2User oauthUser = new CustomOAuth2User(user, Collections.emptyMap());

		assertEquals(user, oauthUser.getUserEntity());
		assertEquals("test@example.com", oauthUser.getName());
		assertNotNull(oauthUser.getAuthorities());
		assertTrue(oauthUser.getAuthorities().isEmpty());
	}

	@Test
	void testConstructor_WithExplicitAuthorities() {
		UserEntity user = new UserEntity();
		user.setEmail("test@example.com");

		Map<String, Object> attributes = Map.of("sub", "999");
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("SCOPE_read"));

		CustomOAuth2User oauthUser = new CustomOAuth2User(user, attributes, authorities);

		assertEquals(user, oauthUser.getUserEntity());
		assertEquals(attributes, oauthUser.getAttributes());
		assertEquals(1, oauthUser.getAuthorities().size());
		assertTrue(oauthUser.getAuthorities().contains(new SimpleGrantedAuthority("SCOPE_read")));
	}

	@Test
	void testConstructor_WithExplicitNullAuthorities() {
		UserEntity user = new UserEntity();
		user.setEmail("test@example.com");

		CustomOAuth2User oauthUser = new CustomOAuth2User(user, Collections.emptyMap(), null);

		assertNotNull(oauthUser.getAuthorities());
		assertTrue(oauthUser.getAuthorities().isEmpty());
	}
}
