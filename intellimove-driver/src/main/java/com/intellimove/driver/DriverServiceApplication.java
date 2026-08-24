package com.intellimove.driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = {"com.intellimove.driver", "com.intellimove.common"})
@ComponentScan(basePackages = {"com.intellimove.driver", "com.intellimove.common"})
@EnableScheduling
@EnableJpaRepositories(basePackages = {"com.intellimove.driver", "com.intellimove.common.outbox"})
public class DriverServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverServiceApplication.class, args);
    }
}
