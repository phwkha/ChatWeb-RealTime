package com.web.backend.oauth2.userinfo;

import java.util.Map;

public class FacebookOAuth2UserInfo extends OAuth2UserInfo {

    public FacebookOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getId() {
        Object id = attributes.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    @Override
    public String getName() {
        return (String) attributes.get("name");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getFirstName() {
        return (String) attributes.get("first_name");
    }

    @Override
    public String getLastName() {
        return (String) attributes.get("last_name");
    }

    @Override
    public String getAvatarUrl() {
        if (attributes.containsKey("picture")) {
            Object pictureObj = attributes.get("picture");
            if (pictureObj instanceof Map<?, ?> pictureMap) {
                Object dataObj = pictureMap.get("data");
                if (dataObj instanceof Map<?, ?> dataMap && dataMap.get("url") != null) {
                    return String.valueOf(dataMap.get("url"));
                }
            } else if (pictureObj instanceof String pictureUrl) {
                return pictureUrl;
            }
        }
        String id = getId();
        return id != null ? "https://graph.facebook.com/" + id + "/picture?type=large" : null;
    }
}
