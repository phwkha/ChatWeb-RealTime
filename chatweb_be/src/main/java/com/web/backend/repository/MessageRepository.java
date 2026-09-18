package com.web.backend.repository;

import com.web.backend.model.mongodb.ChatMessage;
import com.web.backend.repository.projection.UnreadCountProjection;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends MongoRepository<ChatMessage, String> {

        @Query("{ 'conversationId': ?0, 'messageType': 'CHAT' }")
        List<ChatMessage> findByConversationId(String conversationId, Pageable pageable);

        @Query("{ 'conversationId': ?0, 'messageType': 'CHAT', 'timestamp': { '$lt': ?1 } }")
        List<ChatMessage> findByConversationIdAndTimestampBefore(String conversationId, Instant cursor,
                        Pageable pageable);

        @Query(value = "{ $or: [ { 'sender': ?0 }, { 'recipient': ?0 } ] }", exists = true)
        boolean existsBySenderOrRecipient(String username);

        @Aggregation(pipeline = {
                        "{ '$match': { 'recipient': ?0, 'messageType': 'CHAT', 'isDeleted': false } }",
                        "{ '$lookup': { 'from': 'read_receipts', 'let': { 'cId': '$conversationId' }, 'pipeline': [ { '$match': { '$expr': { '$and': [ { '$eq': [ '$conversationId', '$$cId' ] }, { '$eq': [ '$username', ?0 ] } ] } } } ], 'as': 'receipt' } }",
                        "{ '$match': { '$expr': { '$or': [ { '$eq': [ { '$size': '$receipt' }, 0 ] }, { '$eq': [ { '$arrayElemAt': [ '$receipt.lastReadTimestamp', 0 ] }, null ] }, { '$gt': [ '$timestamp', { '$arrayElemAt': [ '$receipt.lastReadTimestamp', 0 ] } ] } ] } } }",
                        "{ '$group': { '_id': '$sender', 'count': { '$sum': 1 } } }",
                        "{ '$project': { 'sender': '$_id', 'count': 1, '_id': 0 } }"
        })
        List<UnreadCountProjection> countUnreadMessagesBySender(String recipientUsername);
}
