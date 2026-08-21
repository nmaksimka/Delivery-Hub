package com.deliveryhub.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRecipientRepository extends JpaRepository<OrderRecipient, Long> {
}
