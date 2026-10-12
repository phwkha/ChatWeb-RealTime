package com.web.backend.oauth2.userinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GoogleOAuth2UserInfoTest {

    @Test
    void testGoogleOAuth2UserInfo_ExtractsAllAttributesCorrectly() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub-12345");
        attributes.put("name", "John Doe");
        attributes.put("email", "john.doe@gmail.com");
        attributes.put("given_name", "John");
        attributes.put("family_name", "Doe");
        attributes.put("picture", "https://lh3.googleusercontent.com/photo.jpg");

        GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(attributes);

        assertEquals("google-sub-12345", userInfo.getId());
        assertEquals("John Doe", userInfo.getName());
        assertEquals("john.doe@gmail.com", userInfo.getEmail());
        assertEquals("John", userInfo.getFirstName());
        assertEquals("Doe", userInfo.getLastName());
        assertEquals("https://lh3.googleusercontent.com/photo.jpg", userInfo.getAvatarUrl());
        assertNotNull(userInfo.getAttributes());
        assertEquals(attributes, userInfo.getAttributes());
    }
}
