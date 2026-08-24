package com.intellimove.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = {"com.intellimove.ride", "com.intellimove.common"})
@ComponentScan(basePackages = {"com.intellimove.ride", "com.intellimove.common"})
@EnableJpaRepositories(basePackages = {"com.intellimove.ride", "com.intellimove.common.outbox"})
@EnableScheduling
public class RideServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}
