package de.unistuttgart.iste.meitrex.assignment_service.controller;


import de.unistuttgart.iste.meitrex.assignment_service.service.AssignmentService;
import de.unistuttgart.iste.meitrex.common.event.ContentChangeEvent;
import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST Controller Class listening to a dapr Topic.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final AssignmentService assignmentService;

    @Topic(name = "content-changed", pubsubName = "meitrex")
    @PostMapping(path = "/assignment-service/content-changed-pubsub")
    public Mono<Void> updateAssociation(@RequestBody CloudEvent<ContentChangeEvent> cloudEvent) {

        return Mono.fromRunnable(() -> {
            try {
                assignmentService.deleteAssignmentIfContentIsDeleted(cloudEvent.getData());
            } catch (Exception e) {
                log.error("Error while processing content-changes event. {}", e.getMessage());
            }
        });
    }

}
