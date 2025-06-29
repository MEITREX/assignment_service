package de.unistuttgart.iste.meitrex.assignment_service.test_config;

import de.unistuttgart.iste.meitrex.assignment_service.service.code_assignment.CodeAssessmentProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;


import static org.mockito.Mockito.mock;

@TestConfiguration
public class MockedCodeAssessmentProviderConfig {
    @Bean(name = "githubClassroom")
    public CodeAssessmentProvider mockedGithubClassroom() {
        return mock(CodeAssessmentProvider.class);
    }
}
