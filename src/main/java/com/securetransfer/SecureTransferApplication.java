package com.securetransfer;

import javafx.application.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.securetransfer")
@EntityScan("com.securetransfer.model")
@EnableJpaRepositories("com.securetransfer.repository")
@EnableScheduling
public class SecureTransferApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureTransferApplication.class, args);
    }
} 