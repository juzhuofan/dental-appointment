package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dental.ai.dto.AiChatPlanDTO;
import com.dental.ai.dto.AiChatTurnDTO;
import com.dental.common.BusinessException;
import com.dental.department.vo.ClinicVO;
import com.dental.department.vo.DepartmentVO;
import com.dental.doctor.vo.DoctorVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiChatAnswerServiceTest {

    private final AiService ai = mock(AiService.class);
    private final AiClinicKnowledgeService knowledge = mock(AiClinicKnowledgeService.class);
    private final AiRateLimitService limiter = mock(AiRateLimitService.class);
    private final AppointmentGuideService guide = mock(AppointmentGuideService.class);
    private AiChatAnswerService service;

    @BeforeEach
    void setUp() {
        var snapshot = new AiClinicKnowledgeService.Snapshot(
                new ClinicVO("测试诊所", "010-12345678", "数据库真实地址", "周一至周六 9:00-18:00", "简介", 120),
                List.of(new DepartmentVO(1L, "牙体牙髓科", "检查", null, null, 1, 1)),
                List.of(new DoctorVO(101L, 901L, 1L, "牙体牙髓科", "测试医生", "医师", "牙痛检查", "简介", "", null, 1)), List.of());
        when(knowledge.load()).thenReturn(snapshot);
        when(knowledge.modelKnowledge(snapshot)).thenReturn(new AiClinicKnowledgeService.ModelKnowledge(snapshot.clinic(),
                List.of(), List.of(), List.of()));
        service = new AiChatAnswerService(ai, knowledge, new AiClinicalGuidanceService(), guide, limiter,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(Instant.parse("2026-10-09T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void clinicFactsComeFromDatabaseNotGeneratedText() {
        givenPlan(plan("CLINIC", true, List.of("ADDRESS", "HOURS"), List.of(), List.of(), List.of(), "GENERAL"));
        var result = service.answer(1, "诊所在哪里", List.of());
        assertTrue(result.content().contains("数据库真实地址"));
        assertTrue(result.content().contains("周一至周六 9:00-18:00"));
        assertEquals(1, result.references().size());
    }

    @Test
    void unpublishedClinicInformationAsksUserToContactClinic() {
        givenPlan(plan("CLINIC", true, List.of("UNPUBLISHED"), List.of(), List.of(), List.of(), "GENERAL"));
        var result = service.answer(1, "洗牙多少钱", List.of());
        assertTrue(result.content().contains("尚未提供"));
        assertTrue(result.content().contains("010-12345678"));
    }

    @Test
    void passesVerifiedHistoryForFollowUpQuestions() {
        var history = List.of(new AiChatTurnDTO("USER", "营业时间是什么"), new AiChatTurnDTO("ASSISTANT", "营业时间"));
        givenPlan(plan("CLINIC", true, List.of("HOURS"), List.of(), List.of(), List.of(), "GENERAL"));
        service.answer(1, "那周末呢", history);
        verify(ai).generateStructured(eq("clinic-chat"), any(), eq(history), eq(AiChatPlanDTO.class));
    }

    @Test
    void rejectsInventedDoctorIdsAndUnapprovedQuestionCodes() {
        givenPlan(plan("DOCTOR", true, List.of(), List.of(), List.of(999L), List.of(), "GENERAL"));
        assertEquals("AI_INVALID_RESPONSE", assertThrows(BusinessException.class,
                () -> service.answer(1, "医生", List.of())).getCode());
        givenPlan(plan("SYMPTOM", true, List.of(), List.of(), List.of(), List.of("用药剂量"), "GENERAL"));
        assertEquals("AI_INVALID_RESPONSE", assertThrows(BusinessException.class,
                () -> service.answer(1, "牙疼", List.of())).getCode());
    }

    @Test
    void missingDoctorDoesNotShowUnrelatedCards() {
        givenPlan(plan("DOCTOR", false, List.of(), List.of(), List.of(), List.of(), "GENERAL"));
        assertTrue(service.answer(1, "不存在的医生", List.of()).candidates().isEmpty());
    }

    @Test
    void immediateRiskBypassesModelQuotaAndBooking() {
        var result = service.answer(1, "牙痛，吞咽困难", List.of());
        assertEquals("EMERGENCY", result.guidance());
        assertTrue(result.candidates().isEmpty());
        assertTrue(result.content().contains("不要等待"));
        verifyNoInteractions(ai, limiter, guide);
    }

    @Test
    void medicalResponseUsesFixedAdviceAndAllowedQuestions() {
        givenPlan(plan("SYMPTOM", false, List.of(), List.of(), List.of(), List.of("DURATION", "LOCATION"), "GENERAL"));
        var result = service.answer(1, "牙疼", List.of());
        assertTrue(result.content().contains("持续多久"));
        assertTrue(result.content().contains("不能判断原因"));
        assertEquals(2, result.references().size());
    }

    private void givenPlan(AiChatPlanDTO plan) {
        when(ai.generateStructured(eq("clinic-chat"), any(), anyList(), eq(AiChatPlanDTO.class))).thenReturn(plan);
    }

    private AiChatPlanDTO plan(String topic, boolean matched, List<String> fields, List<Long> departments,
            List<Long> doctors, List<String> questions, String level) {
        return new AiChatPlanDTO(topic, matched, fields, departments, doctors, "口腔检查", null, null,
                "ANY", List.of(), questions, level);
    }
}
