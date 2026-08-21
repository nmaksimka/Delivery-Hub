package com.deliveryhub.notification.infrastructure.mail;

import com.deliveryhub.notification.domain.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MailComposerTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");

    private final MailComposer composer = new MailComposer(engine());

    @Test
    void orderCreatedMailShowsOrderAndFormattedAmount() {
        Notification notification = Notification.orderCreated(UUID.randomUUID(), 7L, 42L, new BigDecimal("1290.50"), NOW);

        MailComposer.ComposedMail mail = composer.compose(notification, "Анна");

        assertThat(mail.subject()).isEqualTo("Заказ №42 принят");
        assertThat(mail.html()).contains("Анна, здравствуйте!", "№42", "1 290,50 ₽");
    }

    @Test
    void greetsWithoutNameWhenUserHasNone() {
        Notification notification = Notification.orderCancelled(UUID.randomUUID(), 7L, 42L, NOW);

        assertThat(composer.compose(notification, " ").html()).contains("Здравствуйте!").doesNotContain(", здравствуйте");
    }

    @Test
    void escapesUserNameInHtml() {
        Notification notification = Notification.orderDelivered(UUID.randomUUID(), 7L, 42L, NOW);

        assertThat(composer.compose(notification, "<script>").html()).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    @ParameterizedTest
    @MethodSource("everyType")
    void everyNotificationTypeHasTemplate(Notification notification) {
        MailComposer.ComposedMail mail = composer.compose(notification, "Иван");

        assertThat(mail.html()).contains("DeliveryHub", "№42", notification.getTitle());
    }

    static Stream<Notification> everyType() {
        return Stream.of(
                Notification.orderCreated(UUID.randomUUID(), 7L, 42L, BigDecimal.TEN, NOW),
                Notification.paymentRejected(UUID.randomUUID(), 7L, 42L, BigDecimal.TEN, "NO_MONEY", NOW),
                Notification.deliveryRejected(UUID.randomUUID(), 7L, 42L, "NO_COURIER", NOW),
                Notification.courierAssigned(UUID.randomUUID(), 7L, 42L, NOW),
                Notification.orderDelivered(UUID.randomUUID(), 7L, 42L, NOW),
                Notification.orderCancelled(UUID.randomUUID(), 7L, 42L, NOW));
    }

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
