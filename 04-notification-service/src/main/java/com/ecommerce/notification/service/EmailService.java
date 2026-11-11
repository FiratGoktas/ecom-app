package com.ecommerce.notification.service;

import com.ecommerce.notification.dto.OrderCreatedEventDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;

@Service
@Slf4j
public class EmailService {

    static final String TEMPLATE = "order-confirmation";

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        ITemplateEngine templateEngine,
                        @Value("${notification.mail.from}") String from) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.from = from;
    }

    /**
     * Sends the order confirmation email.
     * Permanent failures (no address, bad address, template error) are logged and skipped, since retrying won't help.
     * Transient failures (SMTP down, auth, timeout) are rethrown so the Kafka binder retries and then sends the message to the DLQ.
     */
    public void sendOrderConfirmation(OrderCreatedEventDTO event) {
        String to = event.getUserEmail();
        if (to == null || to.isBlank()) {
            log.warn("Order {} has no user email, skipping confirmation email", event.getOrderId());
            return;
        }

        try {
            Context context = new Context();
            context.setVariable("order", event);
            String html = templateEngine.process(TEMPLATE, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Order #" + event.getOrderId() + " confirmed");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Sent order confirmation for order {}", event.getOrderId());
        } catch (MessagingException | MailParseException | MailPreparationException | TemplateProcessingException e) {
            log.error("Could not build confirmation email for order {}, skipping: {}", event.getOrderId(), e.getMessage());
        } catch (MailException e) {
            log.error("Failed to send confirmation email for order {}, will be retried: {}", event.getOrderId(), e.getMessage());
            throw e;
        }
    }
}
