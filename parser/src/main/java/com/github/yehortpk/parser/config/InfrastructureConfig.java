package com.github.yehortpk.parser.config;

import com.github.yehortpk.parser.models.VacancyMessage;
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
 * Kafka producer of the {@code vacancies} topic: new and removed vacancies as JSON, told apart by
 * the type header.
 */
@Configuration
public class InfrastructureConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${KAFKA_VACANCIES_TOPIC}")
    private String vacanciesTopic;

    /** Declared by every service that uses the topic, so none depends on another starting first. */
    @Bean
    public NewTopic vacanciesTopic() {
        return new NewTopic(vacanciesTopic, 1, (short) 1);
    }

    @Bean
    public ProducerFactory<String, VacancyMessage> vacancyProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.TYPE_MAPPINGS,
                        "newVacancy:com.github.yehortpk.parser.models.NewVacancyDTO,"
                        + "removedVacancy:com.github.yehortpk.parser.models.RemovedVacancyDTO");
        // A send is only reported done once the broker has it, and a retried one is not doubled
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, VacancyMessage> vacancyKafkaTemplate() {
        KafkaTemplate<String, VacancyMessage> kafkaTemplate = new KafkaTemplate<>(vacancyProducerFactory());
        kafkaTemplate.setDefaultTopic(vacanciesTopic);
        return kafkaTemplate;
    }
}
