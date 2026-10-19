package com.github.yehortpk.router.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports the router as down while any Kafka listener container isn't consuming.
 * <p>
 * Spring Boot has no built-in indicator for listener containers, so without this the router
 * answers UP even when the broker was unreachable at startup and no container ever came up —
 * which lets the services that depend on the router start against a router that can't consume.
 */
@Component("kafkaListeners")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class KafkaListenerHealthIndicator implements HealthIndicator {
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Override
    public Health health() {
        List<MessageListenerContainer> containers =
                new ArrayList<>(kafkaListenerEndpointRegistry.getListenerContainers());

        if (containers.isEmpty()) {
            return Health.down().withDetail("reason", "no Kafka listener containers registered").build();
        }

        List<String> idleContainers = containers.stream()
                .filter(container -> !container.isRunning())
                .map(this::describeContainer)
                .toList();

        Health.Builder status = idleContainers.isEmpty() ? Health.up() : Health.down();

        return status
                .withDetail("containers", containers.size())
                .withDetail("notRunning", idleContainers)
                .build();
    }

    private String describeContainer(MessageListenerContainer container) {
        return container.getListenerId() == null ? container.toString() : container.getListenerId();
    }
}
