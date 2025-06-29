package de.unistuttgart.iste.meitrex.assignment_service.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class ExternalSystemConfiguration {

    @Value("${external_system.authToken}")
    private String authToken;

    @Value("${external_system.url}")
    private String basePath;

}
