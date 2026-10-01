package com.web.backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.web.backend.config.localresolverconfig.Translator;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@chatweb.com");
        ResourceBundleMessageSource messageSource = mock(ResourceBundleMessageSource.class);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Email Text");
        Translator.setStaticMessageSource(messageSource);
    }

    @Test
    void testSendTextEmail_Success() {
        emailService.sendTextEmail("recipient@test.com", "Subject", "Content");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendTextEmail_MailSenderThrowsException() {
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> emailService.sendTextEmail("recipient@test.com", "Subject", "Content"));
    }

    @Test
    void testSendOtpEmail_Success() {
        emailService.sendOtpEmail("recipient@test.com", "Bob", "654321");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
