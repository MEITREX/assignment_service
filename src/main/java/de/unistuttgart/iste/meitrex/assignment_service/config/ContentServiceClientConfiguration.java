package de.unistuttgart.iste.meitrex.assignment_service.config;

import de.unistuttgart.iste.meitrex.content_service.client.ContentServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ContentServiceClientConfiguration {

    @Value("${content_service.url}")
    private String contentServiceUrl;

    @Bean
    public ContentServiceClient contentServiceUrl() {
        final WebClient webClient = WebClient.builder().baseUrl(contentServiceUrl).build();
        final GraphQlClient graphQlClient =  HttpGraphQlClient.builder(webClient).build();
        return new ContentServiceClient(graphQlClient);
    }
}
