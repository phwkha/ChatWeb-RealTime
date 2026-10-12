package com.web.backend.oauth2.userinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FacebookOAuth2UserInfoTest {

    @Test
    void testFacebookOAuth2UserInfo_ExtractsAttributesWithNestedPictureMap() {
        Map<String, Object> pictureData = new HashMap<>();
        pictureData.put("url", "https://platform-lookaside.fbsbx.com/pic.jpg");
        Map<String, Object> pictureMap = new HashMap<>();
        pictureMap.put("data", pictureData);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "fb-123456");
        attributes.put("name", "Alice Smith");
        attributes.put("email", "alice@facebook.com");
        attributes.put("first_name", "Alice");
        attributes.put("last_name", "Smith");
        attributes.put("picture", pictureMap);

        FacebookOAuth2UserInfo userInfo = new FacebookOAuth2UserInfo(attributes);

        assertEquals("fb-123456", userInfo.getId());
        assertEquals("Alice Smith", userInfo.getName());
        assertEquals("alice@facebook.com", userInfo.getEmail());
        assertEquals("Alice", userInfo.getFirstName());
        assertEquals("Smith", userInfo.getLastName());
        assertEquals("https://platform-lookaside.fbsbx.com/pic.jpg", userInfo.getAvatarUrl());
    }

    @Test
    void testFacebookOAuth2UserInfo_StringPictureUrl() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "fb-654321");
        attributes.put("picture", "https://example.com/avatar.png");

        FacebookOAuth2UserInfo userInfo = new FacebookOAuth2UserInfo(attributes);

        assertEquals("fb-654321", userInfo.getId());
        assertEquals("https://example.com/avatar.png", userInfo.getAvatarUrl());
    }

    @Test
    void testFacebookOAuth2UserInfo_FallbackToGraphApiPictureUrlWhenPictureMissing() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "fb-789012");

        FacebookOAuth2UserInfo userInfo = new FacebookOAuth2UserInfo(attributes);

        assertEquals("fb-789012", userInfo.getId());
        assertEquals("https://graph.facebook.com/fb-789012/picture?type=large", userInfo.getAvatarUrl());
    }

    @Test
    void testFacebookOAuth2UserInfo_NoIdAndNoPicture_ReturnsNull() {
        Map<String, Object> attributes = new HashMap<>();

        FacebookOAuth2UserInfo userInfo = new FacebookOAuth2UserInfo(attributes);

        assertNull(userInfo.getId());
        assertNull(userInfo.getAvatarUrl());
    }
}
