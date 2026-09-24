package com.web.backend.oauth2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import com.web.backend.config.localresolverconfig.Translator;

class OAuth2AuthenticationFailureHandlerTest {

	private OAuth2AuthenticationFailureHandler failureHandler;

	@BeforeEach
	void setUp() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("i18n/messages");
		messageSource.setDefaultEncoding("UTF-8");
		Translator.setStaticMessageSource(messageSource);

		failureHandler = new OAuth2AuthenticationFailureHandler();
		ReflectionTestUtils.setField(failureHandler, "redirectUri", "http://localhost:3000/oauth2/redirect");
	}

	@Test
	void testOnAuthenticationFailure_RedirectsWithTranslatedErrorParam() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		BadCredentialsException exception = new BadCredentialsException("error.oauth2.email_missing");

		failureHandler.onAuthenticationFailure(request, response, exception);

		String redirectedUrl = response.getRedirectedUrl();
		assertTrue(redirectedUrl != null && redirectedUrl.startsWith("http://localhost:3000/oauth2/redirect?error="));
		assertTrue(redirectedUrl.contains("error_description="));
	}

	@Test
	void testOnAuthenticationFailure_OAuth2AuthenticationException_ExtractsErrorCode() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		org.springframework.security.oauth2.core.OAuth2Error error = 
				new org.springframework.security.oauth2.core.OAuth2Error("error.oauth2.email_already_exists");
		org.springframework.security.oauth2.core.OAuth2AuthenticationException exception = 
				new org.springframework.security.oauth2.core.OAuth2AuthenticationException(error);

		failureHandler.onAuthenticationFailure(request, response, exception);

		String redirectedUrl = response.getRedirectedUrl();
		assertTrue(redirectedUrl != null && redirectedUrl.contains("error="));
		assertTrue(redirectedUrl.contains("error_description="));
	}

	@Test
	void testOnAuthenticationFailure_UnknownErrorCode_FallbacksGracefully() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		org.springframework.security.oauth2.core.OAuth2Error error = 
				new org.springframework.security.oauth2.core.OAuth2Error("unknown.code.that.does.not.exist");
		org.springframework.security.oauth2.core.OAuth2AuthenticationException exception = 
				new org.springframework.security.oauth2.core.OAuth2AuthenticationException(error);

		failureHandler.onAuthenticationFailure(request, response, exception);

		String redirectedUrl = response.getRedirectedUrl();
		assertTrue(redirectedUrl != null && redirectedUrl.contains("error=unknown.code.that.does.not.exist"));
	}
}
