package com.intellimove.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.intellimove.auth", "com.intellimove.common"})
@EntityScan(basePackages = {"com.intellimove.auth", "com.intellimove.common"})
@EnableScheduling
@EnableJpaRepositories(basePackages = {"com.intellimove.auth", "com.intellimove.common.outbox"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
