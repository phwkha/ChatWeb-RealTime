package com.web.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.web.backend.config.localresolverconfig.Translator;

@Slf4j(topic = "EMAIL-SERVICE")
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final String EMAIL_OTP_BODY_STRING = "email.otp.body";
    private static final String EMAIL_OTP_SUBJECT_STRING = "email.otp.subject";

    public void sendTextEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("Email sent successfully to '{}'", to);

        } catch (Exception e) {
            log.error("Failed to send email to '{}'", to, e);
            throw e;
        }
    }

    public void sendOtpEmail(String to, String name, String otp) {
        log.debug("Sending OTP email to '{}'", to);

        String subject = Translator.tolocale(EMAIL_OTP_SUBJECT_STRING);
        String content = Translator.tolocale(EMAIL_OTP_BODY_STRING, name, otp);

        sendTextEmail(to, subject, content);
    }
}