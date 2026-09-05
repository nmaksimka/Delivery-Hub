package com.deliveryhub.order.config;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Без явного обработчика падение в @KafkaListener приводило к бесконечным повторам
 * одного и того же сообщения. Теперь: 3 повтора с паузой, затем запись в topic.DLT.
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    private static final long RETRY_INTERVAL_MS = 2_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    log.error("Routing record from topic {} to DLT", record.topic(), exception);
                    // -1: партицию выбирает брокер, DLT-топик может иметь другое число партиций
                    return new TopicPartition(record.topic() + ".DLT", -1);
                });

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

        // Повторять такое бессмысленно: заказа нет и не появится.
        errorHandler.addNotRetryableExceptions(EntityNotFoundException.class);
        return errorHandler;
    }
}
