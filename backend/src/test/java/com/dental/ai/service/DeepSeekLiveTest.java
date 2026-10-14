package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dental.ai.config.AiConfig;
import com.dental.ai.config.AiProperties;
import com.dental.ai.dto.AppointmentIntentDTO;
import com.dental.ai.service.impl.SpringAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;

/** 仅显式启用时执行一次真实调用，使用虚构公开目录，不读取就诊资料。 */
@EnabledIfSystemProperty(named = "ai.live", matches = "true")
class DeepSeekLiveTest {

    @Test
    @SuppressWarnings("unchecked")
    void parsesWeekendCleaningWithRealProviderAndLocalCredential() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application-dev.yml"));
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey(yaml.getObject().getProperty("app.ai.api-key"));
        ObjectProvider<ChatClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new AiConfig().dentalAiChatClient(properties));
        AiService service = new SpringAiService(provider, properties,
                new PromptTemplateService(new DefaultResourceLoader()), new ObjectMapper().findAndRegisterModules());
        AppointmentIntentDTO result = service.generateStructured("appointment-intent", Map.of(
                "today", "2026-10-09", "departmentId", "", "doctorId", "",
                "departments", "[{\"id\":1,\"name\":\"牙周科\",\"description\":\"洗牙\"}]",
                "doctors", "[{\"id\":101,\"name\":\"测试医生\",\"departmentId\":1,\"specialty\":\"洗牙\"}]",
                "message", "这周末下午想洗牙"), AppointmentIntentDTO.class);
        assertEquals(Boolean.TRUE, result.supported());
        assertEquals("AFTERNOON", result.timeOfDay());
        assertTrue(result.departmentIds().contains(1L));
        assertEquals(java.time.LocalDate.parse("2026-10-10"), result.dateFrom());
        assertEquals(java.time.LocalDate.parse("2026-10-11"), result.dateTo());
    }
}
