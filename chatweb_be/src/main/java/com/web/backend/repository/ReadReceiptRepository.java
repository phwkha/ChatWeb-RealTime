package com.web.backend.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.web.backend.model.mongodb.ReadReceipt;

import java.util.Optional;

public interface ReadReceiptRepository extends MongoRepository<ReadReceipt, String> {

    Optional<ReadReceipt> findByConversationIdAndUsername(String conversationId, String username);
}
