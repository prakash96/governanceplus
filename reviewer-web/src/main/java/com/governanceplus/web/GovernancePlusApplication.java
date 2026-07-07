package com.governanceplus.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GovernancePlusApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovernancePlusApplication.class, args);
    }
}
