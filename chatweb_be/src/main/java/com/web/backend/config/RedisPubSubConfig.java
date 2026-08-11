package com.web.backend.config;

import com.web.backend.redis.RedisSubscriber;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic("channel:server:" + ServerIdentity.SERVER_ID));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisSubscriber subscriber,
            RedisTemplate<String, Object> redisTemplate) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "receiveMessage");
        adapter.setSerializer(redisTemplate.getValueSerializer());
        return adapter;
    }
}
