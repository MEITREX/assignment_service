package de.unistuttgart.iste.meitrex.assignment_service.config;

import de.unistuttgart.iste.meitrex.common.ollama.OllamaClient;
import de.unistuttgart.iste.meitrex.common.config.OllamaConfig;
import de.unistuttgart.iste.meitrex.common.service.JsonSchemaGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class OllamaClientConfiguration {

    @Bean
    public OllamaConfig ollamaConfig() {
        return new OllamaConfig();
    }

    @Bean
    public HttpClient ollamaHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public JsonSchemaGeneratorService jsonSchemaGeneratorService() {
        return new JsonSchemaGeneratorService();
    }

    @Bean
    public OllamaClient ollamaClient(OllamaConfig config,
                                     JsonSchemaGeneratorService schemaService,
                                     ObjectMapper objectMapper,
                                     HttpClient ollamaHttpClient) {
        return new OllamaClient(config, schemaService, objectMapper, ollamaHttpClient);
    }
}
