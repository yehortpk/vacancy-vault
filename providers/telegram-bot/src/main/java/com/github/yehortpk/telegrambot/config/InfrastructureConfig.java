package com.github.yehortpk.telegrambot.config;

import com.github.yehortpk.telegrambot.models.VacancyNotificationDTO;
import org.apache.kafka.clients.admin.AdminClientConfig;
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
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class InfrastructureConfig {
    // Kafka config
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${bot-notifier-topic-consumer-group-id}")
    private String botNotifierTopicConsumerGroupId;

    @Value("${KAFKA_BOT_NOTIFIER_TOPIC}")
    private String botNotifierTopic;

    @Bean
    public NewTopic subscribeTopic() {
        return new NewTopic(botNotifierTopic, 1, (short) 1);
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        return new KafkaAdmin(configs);
    }

    private Map<String, Object> getConsumerFactoryProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "2000");

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return props;
    }

    @Bean
    public ConsumerFactory<String, VacancyNotificationDTO> botNotifierConsumerFactory() {
        Map<String, Object> props = getConsumerFactoryProps();

        props.put(ConsumerConfig.GROUP_ID_CONFIG, botNotifierTopicConsumerGroupId);
        props.put(JsonDeserializer.TYPE_MAPPINGS,
                "vacancy:com.github.yehortpk.telegrambot.models.VacancyNotificationDTO");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VacancyNotificationDTO> botNotifierContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, VacancyNotificationDTO> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(botNotifierConsumerFactory());

        return factory;
    }
}
