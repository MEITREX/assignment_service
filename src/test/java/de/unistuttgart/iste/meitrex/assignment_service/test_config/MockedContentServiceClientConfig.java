package de.unistuttgart.iste.meitrex.assignment_service.test_config;

import de.unistuttgart.iste.meitrex.content_service.client.ContentServiceClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class MockedContentServiceClientConfig {
    @Primary
    @Bean
    public ContentServiceClient contentServiceClient() {
        return mock(ContentServiceClient.class);
    }
}