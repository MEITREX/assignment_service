package de.unistuttgart.iste.meitrex.assignment_service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Arrays;

/**
 * This is the entry point of the application.
 * <p>
 */
@SpringBootApplication
@Slf4j
@EnableAsync
public class AssignmentServiceApplication {

    public static void main(String[] args) {
        Arrays.stream(args).map(arg -> "Received argument: " + arg).forEach(log::info);
        SpringApplication.run(AssignmentServiceApplication.class, args);
    }

}
