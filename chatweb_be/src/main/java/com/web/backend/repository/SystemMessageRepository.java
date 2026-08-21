package com.web.backend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.web.backend.model.mongo.SystemMessage;

import java.time.Instant;
import java.util.List;

public interface SystemMessageRepository extends MongoRepository<SystemMessage, String> {

    @Query("{}")
    List<SystemMessage> findInitialMessage(Pageable pageable);

    @Query("{'timestamp': { '$lt': ?0 } }")
    List<SystemMessage> findMessage(Instant cursor, Pageable pageable);
}
