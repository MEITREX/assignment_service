package de.unistuttgart.iste.meitrex.assignment_service.service;

import de.unistuttgart.iste.meitrex.generated.dto.Template;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.entity.TemplateEntity;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.mapper.TemplateMapper;
import de.unistuttgart.iste.meitrex.assignment_service.persistence.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateMapper templateMapper;

    public List<Template> getAllTemplates() {
        List<TemplateEntity> templates = templateRepository.findAll();
        return templates.stream().map(templateMapper::entityToDto).toList();
    }

}
