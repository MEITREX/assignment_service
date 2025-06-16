package de.unistuttgart.iste.meitrex.assignment_service.test_config;

import de.unistuttgart.iste.meitrex.user_service.client.UserServiceClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class MockedUserServiceClientConfig {
    @Primary
    @Bean
    public UserServiceClient userServiceClient() {
        return mock(UserServiceClient.class);
    }
}