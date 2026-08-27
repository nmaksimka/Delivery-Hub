package com.deliveryhub.notification.infrastructure.mail;

import com.deliveryhub.notification.domain.Notification;
import com.deliveryhub.notification.domain.NotificationRepository;
import com.deliveryhub.notification.infrastructure.users.UserDirectory;
import com.deliveryhub.notification.infrastructure.users.UserDirectory.UserContact;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final NotificationRepository notifications;
    private final UserDirectory userDirectory;
    private final MailComposer composer;
    private final JavaMailSender mailSender;
    private final MailProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public MailDispatcher(NotificationRepository notifications, UserDirectory userDirectory, MailComposer composer,
                          JavaMailSender mailSender, MailProperties properties, TransactionTemplate transactions,
                          Clock clock) {
        this.notifications = notifications;
        this.userDirectory = userDirectory;
        this.composer = composer;
        this.mailSender = mailSender;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.mail.poll-interval}")
    public void dispatchDue() {
        Integer handled = transactions.execute(status -> {
            List<Notification> due = notifications.lockMailsDue(clock.instant(), properties.batchSize());
            due.forEach(this::deliver);
            return due.size();
        });
        if (handled != null && handled > 0) {
            log.debug("Handled {} pending mails", handled);
        }
    }

    private void deliver(Notification notification) {
        try {
            Optional<UserContact> contact = userDirectory.contactOf(notification.getUserId());
            if (contact.isEmpty() || contact.get().email() == null) {
                notification.mailFailed("Recipient not found", clock.instant(), properties.retryBackoff(),
                        properties.maxBackoff(), 1);
                log.warn("No recipient for notification id={}, user id={}", notification.getId(),
                        notification.getUserId());
                return;
            }
            mailSender.send(message(notification, contact.get()));
            notification.mailSent(clock.instant());
            log.info("Mail {} sent for order id={}", notification.getType(), notification.getOrderId());
        } catch (MailException | RestClientException | MessagingException e) {
            notification.mailFailed(e.getMessage(), clock.instant(), properties.retryBackoff(),
                    properties.maxBackoff(), properties.maxAttempts());
            log.warn("Mail for notification id={} failed, attempt {}: {}", notification.getId(),
                    notification.getMailAttempts(), e.getMessage());
        }
    }

    private MimeMessage message(Notification notification, UserContact contact) throws MessagingException {
        MailComposer.ComposedMail mail = composer.compose(notification, contact.name());
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(contact.email());
        helper.setSubject(mail.subject());
        helper.setText(mail.html(), true);
        return message;
    }
}
