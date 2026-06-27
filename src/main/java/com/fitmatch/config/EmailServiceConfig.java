package com.fitmatch.config;

import com.fitmatch.service.EmailService;
import com.fitmatch.service.impl.LoggingEmailService;
import com.fitmatch.service.impl.SmtpEmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailServiceConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.mail.host")
    public EmailService smtpEmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:noreply@fitmatch.app}") String from,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        return new SmtpEmailService(mailSender, from, frontendUrl);
    }

    @Bean
    @ConditionalOnMissingBean(EmailService.class)
    public EmailService loggingEmailService() {
        return new LoggingEmailService();
    }
}
