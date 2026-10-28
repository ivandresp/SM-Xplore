package com.smxplore.sitesandservices.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration(proxyBeanMethods = false)
public class MongoTransactionConfiguration {

    @Bean
    public ReactiveMongoTransactionManager mongoTransactionManager(
            ReactiveMongoDatabaseFactory databaseFactory
    ) {
        return new ReactiveMongoTransactionManager(databaseFactory);
    }

    @Bean("mongoTransactionalOperator")
    @Primary
    public TransactionalOperator mongoTransactionalOperator(
            ReactiveMongoTransactionManager transactionManager
    ) {
        return TransactionalOperator.create(transactionManager);
    }
}
