package com.workpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WorkPilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkPilotApplication.class, args);
    }
}
