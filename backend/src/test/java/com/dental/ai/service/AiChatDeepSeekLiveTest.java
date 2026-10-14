package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dental.ai.config.AiConfig;
import com.dental.ai.config.AiProperties;
import com.dental.ai.dto.AiChatPlanDTO;
import com.dental.ai.dto.AiChatTurnDTO;
import com.dental.ai.service.impl.SpringAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;

/** 虚构公开资料的真实模型验证，不传入真实用户身份或历史。 */
@EnabledIfSystemProperty(named = "ai.live", matches = "true")
class AiChatDeepSeekLiveTest {

    private AiService service;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application-dev.yml"));
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey(yaml.getObject().getProperty("app.ai.api-key"));
        ObjectProvider<ChatClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new AiConfig().dentalAiChatClient(properties));
        service = new SpringAiService(provider, properties,
                new PromptTemplateService(new DefaultResourceLoader()), mapper);
    }

    @Test
    void identifiesClinicFactsWithoutGeneratingAnAddress() throws Exception {
        var result = ask("诊所在哪里，几点营业？", List.of());
        assertEquals("CLINIC", result.topic());
        assertTrue(result.clinicFields().containsAll(List.of("ADDRESS", "HOURS")));
    }

    @Test
    void combinesDoctorSelectionAndWeekendPreferenceAcrossTurns() throws Exception {
        var result = ask("那这周末下午有他的号源吗？", List.of(
                new AiChatTurnDTO("USER", "测试医生甲擅长什么？"),
                new AiChatTurnDTO("ASSISTANT", "测试医生甲擅长牙痛检查。本轮医生卡片：测试医生甲(ID=101,科室=牙体牙髓科)")));
        assertEquals("APPOINTMENT", result.topic());
        assertTrue(result.doctorIds().contains(101L));
        assertEquals("AFTERNOON", result.timeOfDay());
        assertEquals(java.time.LocalDate.parse("2026-10-10"), result.dateFrom());
        assertEquals(java.time.LocalDate.parse("2026-10-11"), result.dateTo());
    }

    @Test
    void producesOnlyAllowedSymptomQuestionCodes() throws Exception {
        var result = ask("我牙齿疼了一天，暂时没有肿胀，也没有呼吸困难，应该怎么安排就诊？", List.of());
        assertEquals("SYMPTOM", result.topic());
        assertTrue(new AiClinicalGuidanceService().validQuestions(result.questions()));
        assertTrue(!"EMERGENCY".equals(result.guidance()));
    }

    private AiChatPlanDTO ask(String message, List<AiChatTurnDTO> history) throws Exception {
        return service.generateStructured("clinic-chat", Map.of("today", "2026-10-09", "message", message,
                "knowledge", "{\"clinic\":{\"name\":\"测试诊所\",\"address\":\"测试街道1号\",\"openingHours\":\"9:00-18:00\"},"
                + "\"departments\":[{\"id\":1,\"name\":\"牙体牙髓科\"}],"
                + "\"doctors\":[{\"id\":101,\"name\":\"测试医生甲\",\"departmentId\":1,\"specialty\":\"牙痛检查\"}]}",
                "guidanceRules", mapper.writeValueAsString(new AiClinicalGuidanceService().rules())), history, AiChatPlanDTO.class);
    }
}
