package com.web.backend.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpUtilsTest {

    @Test
    void testGetClientIpAddress_DirectRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        String ip = IpUtils.getClientIpAddress(request);
        assertEquals("192.168.1.100", ip);
    }

    @Test
    void testGetClientIpAddress_XForwardedFor_Single() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195");
        request.setRemoteAddr("127.0.0.1");

        String ip = IpUtils.getClientIpAddress(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void testGetClientIpAddress_XForwardedFor_Multiple() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("127.0.0.1");

        String ip = IpUtils.getClientIpAddress(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void testGetClientIpAddress_XRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.1");
        request.setRemoteAddr("127.0.0.1");

        String ip = IpUtils.getClientIpAddress(request);
        assertEquals("198.51.100.1", ip);
    }

    @Test
    void testGetClientIpAddress_NullRequest() {
        String ip = IpUtils.getClientIpAddress(null);
        assertEquals("unknown", ip);
    }
}
