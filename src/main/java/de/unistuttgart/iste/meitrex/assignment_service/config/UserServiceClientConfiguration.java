package de.unistuttgart.iste.meitrex.assignment_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class UserServiceClientConfiguration {

    @Value("${user_service.url}")
    private String userServiceUrl;

    @Bean
    public GraphQlClient userServiceClient() {
        final WebClient webClient = WebClient.builder().baseUrl(userServiceUrl).build();
        return HttpGraphQlClient.builder(webClient).build();
    }
}
