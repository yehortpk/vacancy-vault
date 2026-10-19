package com.github.yehortpk.router.config;

import com.github.yehortpk.router.models.vacancy.VacancyDTO;
import com.github.yehortpk.router.models.vacancy.VacancyNotificationDTO;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class InfrastructureConfig {
    // Rest config
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Kafka config
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${parser-topic-consumer-group-id}")
    private String parserTopicConsumerGroupId;

    @Value("${KAFKA_PARSER_TOPIC}")
    private String parserTopic;

    @Bean
    public NewTopic parserTopic() {
        return new NewTopic(parserTopic, 1, (short) 1);
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff fixedBackOff = new FixedBackOff(0L, 0); // no retries
        return new DefaultErrorHandler(fixedBackOff);
    }


    private Map<String, Object> getConsumerFactoryProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Must stay well above the time a full batch of records takes to process, otherwise the
        // broker evicts the consumer mid-batch and redelivers everything it hadn't committed yet.
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50");

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return props;
    }

    @Bean
    public ConsumerFactory<String, VacancyDTO> parserConsumerFactory() {
        Map<String, Object> props = getConsumerFactoryProps();

        props.put(ConsumerConfig.GROUP_ID_CONFIG, parserTopicConsumerGroupId);
        props.put(JsonDeserializer.TYPE_MAPPINGS, "vacancy:com.github.yehortpk.router.models.vacancy.VacancyDTO");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VacancyDTO> parserContainerFactory(
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, VacancyDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(parserConsumerFactory());

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // Producer factory
    @Value("${KAFKA_BOT_NOTIFIER_TOPIC}")
    private String kafkaBotNotifierTopic;

    @Bean
    public ProducerFactory<String, VacancyNotificationDTO> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.TYPE_MAPPINGS,
                "vacancy:com.github.yehortpk.router.models.vacancy.VacancyNotificationDTO");

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, VacancyNotificationDTO> kafkaTemplate() {
        KafkaTemplate<String, VacancyNotificationDTO> kafkaTemplate = new KafkaTemplate<>(producerFactory());
        kafkaTemplate.setDefaultTopic(kafkaBotNotifierTopic);
        return kafkaTemplate;
    }

}
