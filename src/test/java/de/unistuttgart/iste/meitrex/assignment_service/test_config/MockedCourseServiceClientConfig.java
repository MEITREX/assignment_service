package de.unistuttgart.iste.meitrex.assignment_service.test_config;

import de.unistuttgart.iste.meitrex.course_service.client.CourseServiceClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class MockedCourseServiceClientConfig {
    @Primary
    @Bean
    public CourseServiceClient courseServiceClient() {
        return mock(CourseServiceClient.class);
    }
}