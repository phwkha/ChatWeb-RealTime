package com.web.backend.oauth2.userinfo;

import java.util.Collections;
import java.util.Map;

public abstract class OAuth2UserInfo {
    protected final Map<String, Object> attributes;

    protected OAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes != null ? attributes : Collections.emptyMap();
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public String getId() {
        Object id = attributes.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    public String getName() {
        Object name = attributes.get("name");
        return name != null ? String.valueOf(name) : null;
    }

    public String getEmail() {
        Object email = attributes.get("email");
        return email != null ? String.valueOf(email) : null;
    }

    public abstract String getFirstName();

    public abstract String getLastName();

    public abstract String getAvatarUrl();
}
