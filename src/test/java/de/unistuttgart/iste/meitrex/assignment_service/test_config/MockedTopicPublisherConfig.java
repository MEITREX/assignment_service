package de.unistuttgart.iste.meitrex.assignment_service.test_config;

import de.unistuttgart.iste.meitrex.common.dapr.TopicPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration

public class MockedTopicPublisherConfig {
    @Primary
    @Bean
    public TopicPublisher topicPublisher() {
        return mock(TopicPublisher.class);
    }
}
