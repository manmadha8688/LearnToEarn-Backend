package com.example.student.config;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    // Enables @Transactional for multi-document writes (quiz submit, cascaded admin deletes).
    // MongoDB Atlas is a replica set, so multi-document transactions are supported.
    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    /**
     * Production connection-pool + timeout tuning (prod profile only — dev is left as-is).
     * Spring Boot has no property binding for these, so they are applied here to the driver:
     * pool 5..50 connections, 5s max wait for a free connection, 5s socket connect timeout.
     * When the pool/connection times out the driver raises a MongoTimeoutException, which
     * {@code GlobalExceptionHandler} maps to HTTP 503 "Server is busy please try again".
     */
    @Bean
    @Profile("prod")
    public MongoClientSettingsBuilderCustomizer mongoPoolCustomizer() {
        return builder -> builder
            .applyToConnectionPoolSettings(pool -> pool
                .maxSize(50)
                .minSize(5)
                .maxWaitTime(5000, TimeUnit.MILLISECONDS))
            .applyToSocketSettings(socket -> socket
                .connectTimeout(5000, TimeUnit.MILLISECONDS));
    }
}
