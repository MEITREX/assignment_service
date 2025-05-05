package de.unistuttgart.iste.meitrex.assignment_service.persistence.entity;

import de.unistuttgart.iste.meitrex.common.persistence.IWithId;
import jakarta.persistence.*;
import lombok.*;

@Entity(name = "ExternalCourse")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalCourseEntity implements IWithId<String> {

    @Id
    @Column(nullable = false, unique = true)
    private String courseTitle;

    @Column(nullable = false)
    private String url;

    @Override
    public String getId() {
        return courseTitle;
    }
}
