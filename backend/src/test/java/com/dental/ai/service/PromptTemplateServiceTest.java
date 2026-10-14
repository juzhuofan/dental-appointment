package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dental.common.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;

class PromptTemplateServiceTest {

    private final PromptTemplateService service = new PromptTemplateService(new DefaultResourceLoader());

    @ParameterizedTest
    @ValueSource(strings = {"../application-local", "file:/secret", "clinic-copy/system", "", "CLINIC"})
    void rejectsPathTraversalAndExternalResources(String name) {
        assertEquals("INVALID_ARGUMENT", assertThrows(BusinessException.class,
                () -> service.render(name, Map.of())).getCode());
    }

    @Test
    void reportsMissingTemplateAndMissingVariable() {
        assertEquals("NOT_FOUND", assertThrows(BusinessException.class,
                () -> service.render("unknown-template", Map.of())).getCode());
        assertEquals("INVALID_ARGUMENT", assertThrows(BusinessException.class,
                () -> service.render("clinic-copy", Map.of())).getCode());
    }

    @Test
    void treatsBracesInUserDataAsLiteralText() {
        assertTrue(service.render("clinic-copy", Map.of("text", "原文 {secret} <标签>"))
                .user().contains("原文 {secret} <标签>"));
    }
}
