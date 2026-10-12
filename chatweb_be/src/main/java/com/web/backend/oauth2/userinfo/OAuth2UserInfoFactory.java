package com.web.backend.oauth2.userinfo;

import com.web.backend.common.AuthProvider;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Map;

public class OAuth2UserInfoFactory {
    private OAuth2UserInfoFactory() {
    }

    public static OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if (AuthProvider.GOOGLE.name().equalsIgnoreCase(registrationId)) {
            return new GoogleOAuth2UserInfo(attributes);
        } else if (AuthProvider.FACEBOOK.name().equalsIgnoreCase(registrationId)) {
            return new FacebookOAuth2UserInfo(attributes);
        } else if (AuthProvider.GITHUB.name().equalsIgnoreCase(registrationId)) {
            return new GithubOAuth2UserInfo(attributes);
        } else {
            throw new OAuth2AuthenticationException("error.oauth2.unsupported_provider");
        }
    }
}
