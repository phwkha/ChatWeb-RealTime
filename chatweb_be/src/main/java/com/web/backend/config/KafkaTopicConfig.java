package com.web.backend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.topic.chat.messages}")
    private String chatMessagesTopic;

    @Value("${spring.kafka.topic.chat.system-messages}")
    private String systemMessagesTopic;

    @Value("${spring.kafka.topic.email.email-topic}")
    private String emailTopic;

    @Value("${spring.kafka.topic.friend.friend-topic}")
    private String friendTopic;

    @Bean
    public NewTopic chatMessagesTopicBean() {
        return TopicBuilder.name(chatMessagesTopic)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic systemMessagesTopicBean() {
        return TopicBuilder.name(systemMessagesTopic)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic friendTopicBean() {
        return TopicBuilder.name(friendTopic)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailTopicBean() {
        return TopicBuilder.name(emailTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}