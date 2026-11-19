package com.github.yehortpk.dashboard.config;

import com.github.yehortpk.dashboard.models.vacancy.NewVacancyDTO;
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
 * Kafka consumers of the two topics the parser publishes: new vacancies as JSON, removed ones
 * as the bare link. Both run in the same consumer group.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class InfrastructureConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${vacancy-consumer-group-id}")
    private String vacancyConsumerGroupId;

    @Value("${KAFKA_NEW_VACANCIES_TOPIC}")
    private String newVacanciesTopic;

    @Value("${KAFKA_REMOVED_VACANCIES_TOPIC}")
    private String removedVacanciesTopic;

    private Map<String, Object> createConsumerFactoryProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, vacancyConsumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Must stay well above the time a full batch of records takes to process, otherwise the
        // broker evicts the consumer mid-batch and redelivers everything it hadn't committed yet.
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50");

        return props;
    }

    /** Declared by every service that uses the topic, so none depends on another starting first. */
    @Bean
    public NewTopic newVacanciesTopic() {
        return new NewTopic(newVacanciesTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic removedVacanciesTopic() {
        return new NewTopic(removedVacanciesTopic, 1, (short) 1);
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff fixedBackOff = new FixedBackOff(0L, 0); // no retries
        return new DefaultErrorHandler(fixedBackOff);
    }

    @Bean
    public ConsumerFactory<String, NewVacancyDTO> newVacancyConsumerFactory() {
        Map<String, Object> props = createConsumerFactoryProps();
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "newVacancy:com.github.yehortpk.dashboard.models.vacancy.NewVacancyDTO");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NewVacancyDTO> newVacancyContainerFactory(
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, NewVacancyDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(newVacancyConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> removedVacancyConsumerFactory() {
        Map<String, Object> props = createConsumerFactoryProps();
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> removedVacancyContainerFactory(
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(removedVacancyConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
