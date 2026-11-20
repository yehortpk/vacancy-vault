package com.github.yehortpk.parser.config;

import com.github.yehortpk.parser.models.NewVacancyDTO;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producers of the two topics the parser publishes: new vacancies as JSON, removed ones
 * as the bare link.
 */
@Configuration
public class InfrastructureConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${KAFKA_NEW_VACANCIES_TOPIC}")
    private String newVacanciesTopic;

    @Value("${KAFKA_REMOVED_VACANCIES_TOPIC}")
    private String removedVacanciesTopic;

    private Map<String, Object> createProducerFactoryProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // A send is only reported done once the broker has it, and a retried one is not doubled
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
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
    public ProducerFactory<String, NewVacancyDTO> newVacancyProducerFactory() {
        Map<String, Object> props = createProducerFactoryProps();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.TYPE_MAPPINGS, "newVacancy:com.github.yehortpk.parser.models.NewVacancyDTO");

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, NewVacancyDTO> newVacancyKafkaTemplate() {
        KafkaTemplate<String, NewVacancyDTO> kafkaTemplate = new KafkaTemplate<>(newVacancyProducerFactory());
        kafkaTemplate.setDefaultTopic(newVacanciesTopic);
        return kafkaTemplate;
    }

    @Bean
    public ProducerFactory<String, String> removedVacancyProducerFactory() {
        Map<String, Object> props = createProducerFactoryProps();
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> removedVacancyKafkaTemplate() {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(removedVacancyProducerFactory());
        kafkaTemplate.setDefaultTopic(removedVacanciesTopic);
        return kafkaTemplate;
    }
}
