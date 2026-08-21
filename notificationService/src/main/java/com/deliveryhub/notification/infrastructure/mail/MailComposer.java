package com.deliveryhub.notification.infrastructure.mail;

import com.deliveryhub.notification.domain.Notification;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

@Component
public class MailComposer {

    private static final Locale RUSSIAN = Locale.of("ru", "RU");

    private final ITemplateEngine templateEngine;

    public MailComposer(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public ComposedMail compose(Notification notification, String recipientName) {
        Context context = new Context(RUSSIAN, Map.of("notification", notification));
        if (recipientName != null && !recipientName.isBlank()) {
            context.setVariable("name", recipientName);
        }
        String html = templateEngine.process("mail/" + notification.getType().template(), context);
        return new ComposedMail(notification.getTitle(), html);
    }

    public record ComposedMail(String subject, String html) {
    }
}
