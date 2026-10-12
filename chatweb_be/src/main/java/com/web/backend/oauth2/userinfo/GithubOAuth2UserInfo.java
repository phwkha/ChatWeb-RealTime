package com.web.backend.oauth2.userinfo;

import java.util.Map;

public class GithubOAuth2UserInfo extends OAuth2UserInfo {

    public GithubOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getFirstName() {
        String name = getName();
        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+");
            return parts[0];
        }
        return null;
    }

    @Override
    public String getLastName() {
        String name = getName();
        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+");
            if (parts.length > 1) {
                return name.substring(parts[0].length()).trim();
            }
        }
        return null;
    }

    @Override
    public String getAvatarUrl() {
        return (String) attributes.get("avatar_url");
    }
}
