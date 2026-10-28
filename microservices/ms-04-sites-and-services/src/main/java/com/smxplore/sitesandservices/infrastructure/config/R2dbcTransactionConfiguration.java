package com.smxplore.sitesandservices.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration(proxyBeanMethods = false)
public class R2dbcTransactionConfiguration {

    @Bean
    public R2dbcTransactionManager r2dbcTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean("postgresTransactionalOperator")
    public TransactionalOperator postgresTransactionalOperator(
            R2dbcTransactionManager transactionManager
    ) {
        return TransactionalOperator.create(transactionManager);
    }
}
