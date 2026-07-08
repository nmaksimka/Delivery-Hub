package com.deliveryhub.contracts;

import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;

public final class EventPayloads {

    private static final Map<String, BinaryMessageDecoder<? extends SpecificRecord>> DECODERS = Map.of(
            Topics.ORDER_CREATED, OrderCreatedEvent.getDecoder(),
            Topics.DELIVERY_STATUS_UPDATED, DeliveryStatusUpdatedEvent.getDecoder());

    private EventPayloads() {
    }

    public static byte[] encode(SpecificRecord event) {
        BinaryMessageEncoder<SpecificRecord> encoder =
                new BinaryMessageEncoder<>(SpecificData.getForClass(event.getClass()), event.getSchema());
        try {
            ByteBuffer buffer = encoder.encode(event);
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);
            return payload;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode " + event.getSchema().getFullName(), e);
        }
    }

    public static SpecificRecord decode(String topic, byte[] payload) {
        BinaryMessageDecoder<? extends SpecificRecord> decoder = DECODERS.get(topic);
        if (decoder == null) {
            throw new IllegalArgumentException("No event schema is registered for topic " + topic);
        }
        try {
            return decoder.decode(payload);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decode event of topic " + topic, e);
        }
    }
}
