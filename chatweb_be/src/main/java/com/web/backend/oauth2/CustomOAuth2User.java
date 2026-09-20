package com.web.backend.oauth2;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.web.backend.model.postgres.UserEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class CustomOAuth2User implements OAuth2User {

    @Getter
    private final UserEntity userEntity;
    private final Map<String, Object> attributes;
    private final Set<? extends GrantedAuthority> authorities;

    public CustomOAuth2User(UserEntity userEntity, Map<String, Object> attributes) {
        this.userEntity = userEntity;
        this.attributes = attributes;
        this.authorities = (userEntity != null && userEntity.getAuthorities() != null)
                ? Set.copyOf(userEntity.getAuthorities())
                : Collections.emptySet();
    }

    public CustomOAuth2User(UserEntity userEntity, Map<String, Object> attributes,
            Collection<? extends GrantedAuthority> authorities) {
        this.userEntity = userEntity;
        this.attributes = attributes;
        this.authorities = authorities != null ? Set.copyOf(authorities) : Collections.emptySet();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return userEntity != null ? userEntity.getEmail() : null;
    }
}
