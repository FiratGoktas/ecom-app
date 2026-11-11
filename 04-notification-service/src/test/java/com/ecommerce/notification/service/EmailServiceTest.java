package com.ecommerce.notification.service;

import com.ecommerce.notification.dto.OrderCreatedEventDTO;
import com.ecommerce.notification.dto.OrderItemDTO;
import com.ecommerce.notification.enums.OrderStatus;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        // A plain mock would return null here and the service would hit an NPE
        when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        emailService = new EmailService(mailSender, engine, "no-reply@ecommerce.local");
    }

    @Test
    void sendsConfirmationWithRecipientSubjectAndItems() throws Exception {
        emailService.sendOrderConfirmation(event("jane@example.com", "Jane"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();

        assertThat(sent.getAllRecipients()).containsExactly(new InternetAddress("jane@example.com"));
        assertThat(sent.getFrom()).containsExactly(new InternetAddress("no-reply@ecommerce.local"));
        assertThat(sent.getSubject()).isEqualTo("Order #42 confirmed");

        String body = bodyOf(sent);
        assertThat(body).contains("Jane", "#42", "CONFIRMED", "101", "202", "35.00");
    }

    @Test
    void skipsWhenEmailMissing() {
        emailService.sendOrderConfirmation(event(null, "Jane"));
        emailService.sendOrderConfirmation(event("  ", "Jane"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void rethrowsTransientSendFailureSoBinderRetries() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendOrderConfirmation(event("jane@example.com", "Jane")))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    void escapesUserControlledName() throws Exception {
        emailService.sendOrderConfirmation(event("jane@example.com", "<script>alert(1)</script>"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        String body = bodyOf(captor.getValue());

        assertThat(body).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    private static OrderCreatedEventDTO event(String email, String firstName) {
        OrderCreatedEventDTO e = new OrderCreatedEventDTO();
        e.setOrderId(42L);
        e.setUserId("u-1");
        e.setUserEmail(email);
        e.setUserFirstName(firstName);
        e.setUserLastName("Doe");
        e.setOrderStatus(OrderStatus.CONFIRMED);
        e.setItems(List.of(
                new OrderItemDTO(1L, 101L, 2, new BigDecimal("10.00"), new BigDecimal("20.00")),
                new OrderItemDTO(2L, 202L, 1, new BigDecimal("15.00"), new BigDecimal("15.00"))));
        e.setTotalAmount(new BigDecimal("35.00"));
        e.setCreatedAt(LocalDateTime.of(2026, 11, 12, 10, 30));
        return e;
    }

    /** Returns the decoded text/html part, so quoted-printable encoding doesn't break the assertions. */
    private static String bodyOf(MimeMessage message) throws Exception {
        message.saveChanges();
        String html = findHtml(message);
        assertThat(html).as("text/html part").isNotNull();
        return html;
    }

    private static String findHtml(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = findHtml(multipart.getBodyPart(i));
                if (html != null) {
                    return html;
                }
            }
        }
        return null;
    }
}
