package com.github.yehortpk.dashboard.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer of the {@code vacancies} topic the crawler publishes: new and removed vacancies
 * as JSON, told apart by the type header.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class InfrastructureConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${vacancy-consumer-group-id}")
    private String vacancyConsumerGroupId;

    @Value("${KAFKA_VACANCIES_TOPIC}")
    private String vacanciesTopic;

    @Bean
    public ConsumerFactory<String, Object> vacancyConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, vacancyConsumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Must stay well above the time a full batch of records takes to process, otherwise the
        // broker evicts the consumer mid-batch and redelivers everything it hadn't committed yet.
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                        "newVacancy:com.github.yehortpk.dashboard.models.vacancy.NewVacancyDTO,"
                        + "removedVacancy:com.github.yehortpk.dashboard.models.vacancy.RemovedVacancyDTO");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /** Declared by every service that uses the topic, so none depends on another starting first. */
    @Bean
    public NewTopic vacanciesTopic() {
        return new NewTopic(vacanciesTopic, 1, (short) 1);
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff fixedBackOff = new FixedBackOff(0L, 0); // no retries
        return new DefaultErrorHandler(fixedBackOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> vacancyContainerFactory(
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(vacancyConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
