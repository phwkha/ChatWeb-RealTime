package com.web.backend.config;

import com.web.backend.kafka.avro.ChatMessageAvro;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092,localhost:9094");
        ReflectionTestUtils.setField(kafkaConfig, "schemaRegistryUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(kafkaConfig, "chatMessagesTopic", "chat-messages");
    }

    @Test
    void testCommonKafkaErrorHandler_Configuration() {
        DefaultErrorHandler errorHandler = kafkaConfig.commonKafkaErrorHandler();
        assertNotNull(errorHandler);

        // Check BackOff configuration: 1000ms, max 3 attempts
        Object failureTracker = ReflectionTestUtils.getField(errorHandler, "failureTracker");
        assertNotNull(failureTracker);
        FixedBackOff backOff = (FixedBackOff) ReflectionTestUtils.getField(failureTracker, "backOff");
        assertNotNull(backOff);
        assertEquals(1000L, backOff.getInterval());
        assertEquals(3L, backOff.getMaxAttempts());

        // Check Non-retryable exceptions
        BinaryExceptionClassifier classifier = (BinaryExceptionClassifier) ReflectionTestUtils.invokeMethod(errorHandler, "getClassifier");
        assertNotNull(classifier);
        // Non-retryable exceptions return false
        assertFalse(classifier.classify(new SerializationException("Deserialization failed")));
        assertFalse(classifier.classify(new RecordTooLargeException("Payload too large")));
        assertFalse(classifier.classify(new IllegalArgumentException("Invalid argument")));
        // Other unexpected runtime exceptions return true (retryable)
        assertTrue(classifier.classify(new RuntimeException("Transient DB error")));
    }

    @Test
    void testDltErrorHandler_Configuration() {
        DefaultErrorHandler errorHandler = kafkaConfig.dltErrorHandler();
        assertNotNull(errorHandler);

        Object failureTracker = ReflectionTestUtils.getField(errorHandler, "failureTracker");
        assertNotNull(failureTracker);
        FixedBackOff backOff = (FixedBackOff) ReflectionTestUtils.getField(failureTracker, "backOff");
        assertNotNull(backOff);
        assertEquals(5000L, backOff.getInterval());
        assertEquals(5L, backOff.getMaxAttempts());
    }

    @Test
    void testCommonKafkaErrorHandler_NonRetryableException_ImmediatelyRecovers() {
        DefaultErrorHandler errorHandler = kafkaConfig.commonKafkaErrorHandler();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 100L, "key", "val");
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        // For non-retryable exception like SerializationException, handleOne should recover immediately (returns true)
        boolean handled = errorHandler.handleOne(
                new SerializationException("Bad payload"),
                record,
                consumer,
                container
        );
        assertTrue(handled, "Non-retryable exception should be handled/recovered immediately without retry");
    }

    @Test
    void testCommonKafkaErrorHandler_RetryableException_RetriesBeforeExhaustion() {
        DefaultErrorHandler errorHandler = kafkaConfig.commonKafkaErrorHandler();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 101L, "key", "val");
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        RuntimeException retryableException = new RuntimeException("DB connection timeout");

        // Attempt 1, 2, 3: should not be recovered yet (returns false to seek back and retry)
        for (int i = 1; i <= 3; i++) {
            boolean handled = errorHandler.handleOne(retryableException, record, consumer, container);
            assertFalse(handled, "Attempt " + i + " of retryable exception should return false (seek back to retry)");
        }

        // Attempt 4: Backoff exhausted after 3 retries, should recover and return true
        boolean attempt4 = errorHandler.handleOne(retryableException, record, consumer, container);
        assertTrue(attempt4, "Attempt 4 after 3 retries should be exhausted and recovered (returns true)");
    }

    @Test
    void testDltErrorHandler_RetryableException_ExhaustsAfter5Attempts() {
        DefaultErrorHandler errorHandler = kafkaConfig.dltErrorHandler();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("chat-messages-save-dlt", 0, 200L, "key", "val");
        Consumer<?, ?> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        RuntimeException mongoEx = new RuntimeException("Mongo write failure");

        // Attempts 1 to 5: retry attempts
        for (int i = 1; i <= 5; i++) {
            boolean handled = errorHandler.handleOne(mongoEx, record, consumer, container);
            assertFalse(handled, "Attempt " + i + " should not be exhausted yet");
        }

        // Attempt 6 should exhaust (after 5 retries) and recover
        boolean attempt6 = errorHandler.handleOne(mongoEx, record, consumer, container);
        assertTrue(attempt6, "Attempt 6 should exhaust retries and return true");
    }

    @Test
    void testChatAvroListenerContainerFactory_ConfiguredWithCommonErrorHandler() {
        DefaultErrorHandler commonErrorHandler = kafkaConfig.commonKafkaErrorHandler();
        ConcurrentKafkaListenerContainerFactory<String, ChatMessageAvro> factory =
                kafkaConfig.chatAvroListenerContainerFactory(commonErrorHandler);

        assertNotNull(factory);
        assertEquals(4, ReflectionTestUtils.getField(factory, "concurrency"));
        assertSame(commonErrorHandler, ReflectionTestUtils.getField(factory, "commonErrorHandler"));
    }

    @Test
    void testJsonKafkaListenerContainerFactory_ConfiguredWithCommonErrorHandler() {
        DefaultErrorHandler commonErrorHandler = kafkaConfig.commonKafkaErrorHandler();
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                kafkaConfig.jsonKafkaListenerContainerFactory(commonErrorHandler);

        assertNotNull(factory);
        assertEquals(2, ReflectionTestUtils.getField(factory, "concurrency"));
        assertSame(commonErrorHandler, ReflectionTestUtils.getField(factory, "commonErrorHandler"));
    }

    @Test
    void testEmailKafkaListenerContainerFactory_ConfiguredWithCommonErrorHandlerAndManualAck() {
        DefaultErrorHandler commonErrorHandler = kafkaConfig.commonKafkaErrorHandler();
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                kafkaConfig.emailKafkaListenerContainerFactory(commonErrorHandler);

        assertNotNull(factory);
        assertSame(commonErrorHandler, ReflectionTestUtils.getField(factory, "commonErrorHandler"));
        assertEquals(ContainerProperties.AckMode.MANUAL_IMMEDIATE, factory.getContainerProperties().getAckMode());
    }

    @Test
    void testBatchChatAvroListenerContainerFactory_Configuration() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, ChatMessageAvro> template = mock(KafkaTemplate.class);
        DefaultErrorHandler batchErrorHandler = kafkaConfig.batchChatAvroErrorHandler(template);
        assertNotNull(batchErrorHandler);

        ConcurrentKafkaListenerContainerFactory<String, ChatMessageAvro> factory =
                kafkaConfig.batchChatAvroListenerContainerFactory(batchErrorHandler);

        assertNotNull(factory);
        assertTrue(factory.isBatchListener());
        assertEquals(2, ReflectionTestUtils.getField(factory, "concurrency"));
        assertSame(batchErrorHandler, ReflectionTestUtils.getField(factory, "commonErrorHandler"));
    }

    @Test
    void testDltChatAvroListenerContainerFactory_Configuration() {
        DefaultErrorHandler dltErrorHandler = kafkaConfig.dltErrorHandler();
        ConcurrentKafkaListenerContainerFactory<String, ChatMessageAvro> factory =
                kafkaConfig.dltChatAvroListenerContainerFactory(dltErrorHandler);

        assertNotNull(factory);
        assertEquals(2, ReflectionTestUtils.getField(factory, "concurrency"));
        assertSame(dltErrorHandler, ReflectionTestUtils.getField(factory, "commonErrorHandler"));
    }

    @Test
    void testBaseAvroConsumerProps_UsesErrorHandlingDeserializer() {
        ConsumerFactory<String, ChatMessageAvro> factory = kafkaConfig.realtimeChatConsumerFactory();
        assertNotNull(factory);

        Map<String, Object> configs = factory.getConfigurationProperties();
        assertEquals(ErrorHandlingDeserializer.class, configs.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(ErrorHandlingDeserializer.class, configs.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(StringDeserializer.class, configs.get(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS));
        assertEquals("localhost:9092,localhost:9094", configs.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("http://localhost:8081", configs.get("schema.registry.url"));
    }

    @Test
    void testBaseJsonConsumerProps_UsesErrorHandlingDeserializer() {
        ConsumerFactory<String, Object> factory = kafkaConfig.jsonConsumerFactory();
        assertNotNull(factory);

        Map<String, Object> configs = factory.getConfigurationProperties();
        assertEquals(ErrorHandlingDeserializer.class, configs.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(ErrorHandlingDeserializer.class, configs.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(StringDeserializer.class, configs.get(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS));
        assertEquals(JsonDeserializer.class, configs.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS));
        assertEquals("*", configs.get(JsonDeserializer.TRUSTED_PACKAGES));
    }

    @Test
    void testProducerFactories_Configuration() {
        ProducerFactory<String, ChatMessageAvro> avroProducerFactory = kafkaConfig.avroChatProducerFactory();
        assertNotNull(avroProducerFactory);
        Map<String, Object> avroConfigs = avroProducerFactory.getConfigurationProperties();
        assertEquals("all", avroConfigs.get(ProducerConfig.ACKS_CONFIG));
        assertEquals(true, avroConfigs.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));

        ProducerFactory<String, Object> jsonProducerFactory = kafkaConfig.jsonProducerFactory();
        assertNotNull(jsonProducerFactory);
        Map<String, Object> jsonConfigs = jsonProducerFactory.getConfigurationProperties();
        assertEquals("all", jsonConfigs.get(ProducerConfig.ACKS_CONFIG));
        assertEquals(true, jsonConfigs.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
    }
}
