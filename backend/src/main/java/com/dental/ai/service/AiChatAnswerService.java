package com.dental.ai.service;

import com.dental.ai.dto.AiChatPlanDTO;
import com.dental.ai.dto.AiChatTurnDTO;
import com.dental.ai.dto.AppointmentGuideDTO;
import com.dental.ai.dto.AppointmentIntentDTO;
import com.dental.ai.vo.AiChatAnswerVO;
import com.dental.ai.vo.AiReferenceVO;
import com.dental.ai.vo.AppointmentGuideVO;
import com.dental.common.BusinessException;
import com.dental.common.TimeUtil;
import com.dental.department.vo.ClinicVO;
import com.dental.doctor.vo.DoctorVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 将 AI 查询计划转换为真实事实、经校验的医生卡片和有出处的初步导诊。 */
@Service
public class AiChatAnswerService {

    private static final Set<String> TOPICS = Set.of("CLINIC", "DOCTOR", "APPOINTMENT", "SYMPTOM", "OTHER");
    private static final Set<String> CLINIC_FIELDS = Set.of("NAME", "ADDRESS", "HOURS", "PHONE", "INTRODUCTION", "CANCEL_RULE", "NOTICES", "UNPUBLISHED");
    private static final Set<String> GUIDANCE_LEVELS = Set.of("GENERAL", "PROMPT_DENTIST", "EMERGENCY");
    private static final Set<String> TIME_PERIODS = Set.of("ANY", "MORNING", "AFTERNOON", "EVENING");
    private final AiService aiService;
    private final AiClinicKnowledgeService knowledgeService;
    private final AiClinicalGuidanceService clinicalService;
    private final AppointmentGuideService guideService;
    private final AiRateLimitService rateLimitService;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AiChatAnswerService(AiService aiService, AiClinicKnowledgeService knowledgeService,
            AiClinicalGuidanceService clinicalService, AppointmentGuideService guideService,
            AiRateLimitService rateLimitService, ObjectMapper mapper, @Qualifier("aiClock") Clock clock) {
        this.aiService = aiService;
        this.knowledgeService = knowledgeService;
        this.clinicalService = clinicalService;
        this.guideService = guideService;
        this.rateLimitService = rateLimitService;
        this.mapper = mapper;
        this.clock = clock.withZone(TimeUtil.CLINIC_ZONE);
    }

    public AiChatAnswerVO answer(long userId, String message, List<AiChatTurnDTO> history) {
        if (clinicalService.hasImmediateRisk(message)) {
            return emergency();
        }
        rateLimitService.check(userId);
        AiClinicKnowledgeService.Snapshot snapshot = knowledgeService.load();
        AiChatPlanDTO plan = aiService.generateStructured("clinic-chat", Map.of(
                "today", LocalDate.now(clock).toString(), "message", message,
                "knowledge", json(knowledgeService.modelKnowledge(snapshot)),
                "guidanceRules", json(clinicalService.rules())), history, AiChatPlanDTO.class);
        validate(plan, snapshot);
        if ("EMERGENCY".equals(plan.guidance())) {
            return emergency();
        }
        return switch (plan.topic()) {
            case "CLINIC" -> clinicAnswer(plan, snapshot);
            case "DOCTOR" -> doctorAnswer(plan, snapshot);
            case "APPOINTMENT" -> appointmentAnswer(message, plan);
            case "SYMPTOM" -> symptomAnswer(message, plan);
            default -> new AiChatAnswerVO("我可以帮你了解诊所营业时间、预约规则、医生擅长方向和号源，"
                    + "也可以根据口腔问题提供初步就诊提示。你想先了解哪一方面？", "OTHER", "GENERAL", List.of(), List.of());
        };
    }

    private AiChatAnswerVO clinicAnswer(AiChatPlanDTO plan, AiClinicKnowledgeService.Snapshot snapshot) {
        ClinicVO clinic = snapshot.clinic();
        List<String> fields = list(plan.clinicFields()).isEmpty()
                ? List.of("NAME", "ADDRESS", "HOURS", "PHONE") : plan.clinicFields();
        String content = fields.stream().distinct().map(field -> switch (field) {
            case "NAME" -> "诊所名称：" + value(clinic.name());
            case "ADDRESS" -> "诊所地址：" + value(clinic.address());
            case "HOURS" -> "营业时间：" + value(clinic.openingHours());
            case "PHONE" -> "联系电话：" + value(clinic.phone());
            case "INTRODUCTION" -> "诊所介绍：" + value(clinic.introduction());
            case "CANCEL_RULE" -> "预约提交后需由诊所确认；待确认或待就诊的预约可在就诊开始前 "
                    + clinic.cancelBeforeMinutes() + " 分钟以前取消。具体排班可能使用不同截止时间，请核对预约详情。";
            case "NOTICES" -> snapshot.notices().isEmpty() ? "当前没有已发布公告。"
                    : "最新公告：\n" + snapshot.notices().stream().map(notice -> notice.title() + "：" + value(notice.content()))
                    .collect(Collectors.joining("\n"));
            case "UNPUBLISHED" -> "诊所当前公开资料尚未提供这项信息，请联系诊所确认。联系电话：" + value(clinic.phone());
            default -> "";
        }).collect(Collectors.joining("\n\n"));
        return new AiChatAnswerVO(limit(content, 2000), "CLINIC", "GENERAL", List.of(),
                List.of(new AiReferenceVO("依据：诊所当前公开配置与已发布公告", "")));
    }

    private AiChatAnswerVO doctorAnswer(AiChatPlanDTO plan, AiClinicKnowledgeService.Snapshot snapshot) {
        List<DoctorVO> doctors = Boolean.FALSE.equals(plan.catalogMatched()) ? List.of() : snapshot.doctors().stream()
                .filter(doctor -> list(plan.doctorIds()).isEmpty() || plan.doctorIds().contains(doctor.id()))
                .filter(doctor -> list(plan.departmentIds()).isEmpty() || plan.departmentIds().contains(doctor.departmentId()))
                .limit(5).toList();
        List<AppointmentGuideVO.Candidate> cards = doctors.stream().map(doctor -> new AppointmentGuideVO.Candidate(
                doctor.id(), doctor.name(), doctor.title(), doctor.departmentId(), doctor.departmentName(),
                doctor.specialty(), List.of())).toList();
        String content = cards.isEmpty() ? "当前公开目录中没有找到对应医生或科室，请换一个姓名、科室或擅长方向查询。"
                : "以下是诊所当前公开的医生资料：\n\n" + doctors.stream().map(doctor -> doctor.name()
                + "（" + doctor.departmentName() + "）\n擅长方向：" + value(doctor.specialty())
                + "\n简介：" + value(doctor.introduction())).collect(Collectors.joining("\n\n"));
        return new AiChatAnswerVO(limit(content, 2000), "DOCTOR", "GENERAL", cards,
                List.of(new AiReferenceVO("依据：诊所当前已启用的医生与科室资料", "")));
    }

    private AiChatAnswerVO appointmentAnswer(String message, AiChatPlanDTO plan) {
        if (!Boolean.TRUE.equals(plan.catalogMatched())) {
            return new AiChatAnswerVO("当前目录没有匹配的科室或医生，请补充希望就诊的医生、科室或目的。",
                    "APPOINTMENT", "GENERAL", List.of(), List.of());
        }
        AppointmentGuideVO result = recommend(message, plan);
        return new AiChatAnswerVO(result.message() + (result.dateFrom() == null ? ""
                : "\n查询日期：" + result.dateFrom() + " 至 " + result.dateTo()),
                "APPOINTMENT", "GENERAL", result.candidates(),
                List.of(new AiReferenceVO("依据：诊所当前已发布排班；余量以提交时为准", "")));
    }

    private AiChatAnswerVO symptomAnswer(String message, AiChatPlanDTO plan) {
        if ("EMERGENCY".equals(plan.guidance())) {
            return emergency();
        }
        String level = plan.guidance() == null ? "GENERAL" : plan.guidance();
        List<AppointmentGuideVO.Candidate> cards = List.of();
        String content = clinicalService.advice(level, plan.questions());
        if (Boolean.TRUE.equals(plan.catalogMatched())
                && (!list(plan.doctorIds()).isEmpty() || !list(plan.departmentIds()).isEmpty())) {
            AppointmentGuideVO result = recommend(message, plan);
            cards = result.candidates();
            content += "\n\n" + (cards.isEmpty() ? "对应科室当前条件下暂无可预约号源，请联系诊所确认。"
                    : "以下是相关科室的可预约医生，仅供就诊方向选择；具体诊疗方案由医生检查后确定。");
        }
        return new AiChatAnswerVO(content, "SYMPTOM", level, cards, clinicalService.references());
    }

    private AppointmentGuideVO recommend(String message, AiChatPlanDTO plan) {
        String purpose = plan.purpose() == null || plan.purpose().isBlank() ? "口腔检查" : plan.purpose();
        return guideService.recommend(new AppointmentGuideDTO(message, null, null),
                new AppointmentIntentDTO(true, purpose, plan.departmentIds(), plan.doctorIds(), plan.dateFrom(),
                        plan.dateTo(), plan.timeOfDay(), plan.weekdays(), ""));
    }

    private void validate(AiChatPlanDTO plan, AiClinicKnowledgeService.Snapshot snapshot) {
        if (plan == null || plan.topic() == null || !TOPICS.contains(plan.topic())
                || (Set.of("DOCTOR", "APPOINTMENT", "SYMPTOM").contains(plan.topic()) && plan.catalogMatched() == null)
                || (plan.guidance() != null && !GUIDANCE_LEVELS.contains(plan.guidance()))
                || list(plan.clinicFields()).stream().anyMatch(java.util.Objects::isNull)
                || !CLINIC_FIELDS.containsAll(list(plan.clinicFields())) || !clinicalService.validQuestions(plan.questions())
                || (plan.purpose() != null && plan.purpose().length() > 80)
                || (plan.timeOfDay() != null && !TIME_PERIODS.contains(plan.timeOfDay()))
                || !snapshot.doctors().stream().map(DoctorVO::id).collect(Collectors.toSet()).containsAll(list(plan.doctorIds()))
                || !snapshot.departments().stream().map(com.dental.department.vo.DepartmentVO::id)
                .collect(Collectors.toSet()).containsAll(list(plan.departmentIds()))
                || list(plan.weekdays()).stream().anyMatch(day -> day == null || day < 1 || day > 7)) {
            throw new BusinessException("AI_INVALID_RESPONSE", "暂未能准确理解问题，请换一种说法再试", HttpStatus.BAD_GATEWAY);
        }
    }

    private AiChatAnswerVO emergency() {
        return new AiChatAnswerVO(clinicalService.advice("EMERGENCY", List.of()), "SYMPTOM", "EMERGENCY",
                List.of(), clinicalService.references());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("公开资料序列化失败");
        }
    }

    private String value(String text) {
        return text == null || text.isBlank() ? "尚未公布，请联系诊所确认" : limit(text, 700);
    }

    private String limit(String text, int max) {
        return text.substring(0, Math.min(text.length(), max));
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
