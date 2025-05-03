package de.unistuttgart.iste.meitrex.assignment_service.config;

import de.unistuttgart.iste.meitrex.user_service.client.UserServiceClient;
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
    public UserServiceClient userServiceClient() {
        final WebClient webClient = WebClient.builder().baseUrl(userServiceUrl).build();
        final GraphQlClient graphQlClient =  HttpGraphQlClient.builder(webClient).build();
        return new UserServiceClient(graphQlClient);
    }
}
