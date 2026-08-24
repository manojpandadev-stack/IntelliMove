package com.intellimove.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = {"com.intellimove.payment", "com.intellimove.common"})
@ComponentScan(basePackages = {"com.intellimove.payment", "com.intellimove.common"})
@EnableJpaRepositories(basePackages = {"com.intellimove.payment", "com.intellimove.common.outbox"})
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
