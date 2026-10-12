package com.web.backend.oauth2.userinfo;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class OAuth2UserInfoFactoryTest {

    @Test
    void testGetOAuth2UserInfo_Google_ReturnsGoogleOAuth2UserInfo() {
        Map<String, Object> attributes = Map.of("sub", "123", "email", "test@gmail.com");
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("google", attributes);

        assertNotNull(userInfo);
        assertInstanceOf(GoogleOAuth2UserInfo.class, userInfo);
    }

    @Test
    void testGetOAuth2UserInfo_Facebook_ReturnsFacebookOAuth2UserInfo() {
        Map<String, Object> attributes = Map.of("id", "456", "email", "test@facebook.com");
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("facebook", attributes);

        assertNotNull(userInfo);
        assertInstanceOf(FacebookOAuth2UserInfo.class, userInfo);
    }

    @Test
    void testGetOAuth2UserInfo_Github_ReturnsGithubOAuth2UserInfo() {
        Map<String, Object> attributes = Map.of("id", 789, "email", "test@github.com");
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("github", attributes);

        assertNotNull(userInfo);
        assertInstanceOf(GithubOAuth2UserInfo.class, userInfo);
    }

    @Test
    void testGetOAuth2UserInfo_UnsupportedProvider_ThrowsOAuth2AuthenticationException() {
        OAuth2AuthenticationException exception = assertThrows(OAuth2AuthenticationException.class,
                () -> OAuth2UserInfoFactory.getOAuth2UserInfo("unsupported_provider", Collections.emptyMap()));

        assertEquals("error.oauth2.unsupported_provider", exception.getError().getErrorCode());
    }
}
