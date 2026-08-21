package com.web.backend.oauth2;

import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.web.backend.model.postgres.UserEntity;

import java.util.Collection;
import java.util.Map;

@AllArgsConstructor
public class CustomOAuth2User implements OAuth2User {

    private UserEntity userEntity;
    private Map<String, Object> attributes;

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return userEntity.getAuthorities();
    }

    @Override
    public String getName() {
        return userEntity.getEmail();
    }

    public UserEntity getUserEntity() {
        return userEntity;
    }
}
