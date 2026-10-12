package com.web.backend.oauth2.userinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GithubOAuth2UserInfoTest {

    @Test
    void testGithubOAuth2UserInfo_ExtractsAttributesCorrectly() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 12345678);
        attributes.put("name", "Bob Dylan");
        attributes.put("email", "bob@github.com");
        attributes.put("avatar_url", "https://avatars.githubusercontent.com/u/12345678");

        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(attributes);

        assertEquals("12345678", userInfo.getId());
        assertEquals("Bob Dylan", userInfo.getName());
        assertEquals("bob@github.com", userInfo.getEmail());
        assertEquals("Bob", userInfo.getFirstName());
        assertEquals("Dylan", userInfo.getLastName());
        assertEquals("https://avatars.githubusercontent.com/u/12345678", userInfo.getAvatarUrl());
    }

    @Test
    void testGithubOAuth2UserInfo_SingleWordName() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "87654321");
        attributes.put("name", "Octocat");

        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(attributes);

        assertEquals("87654321", userInfo.getId());
        assertEquals("Octocat", userInfo.getName());
        assertEquals("Octocat", userInfo.getFirstName());
        assertNull(userInfo.getLastName());
    }

    @Test
    void testGithubOAuth2UserInfo_NullName() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "87654321");

        GithubOAuth2UserInfo userInfo = new GithubOAuth2UserInfo(attributes);

        assertNull(userInfo.getName());
        assertNull(userInfo.getFirstName());
        assertNull(userInfo.getLastName());
    }
}
