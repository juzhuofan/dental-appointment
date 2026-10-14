package com.dental.ai.service;

import com.dental.ai.dto.AppointmentGuideDTO;
import com.dental.ai.dto.AppointmentIntentDTO;
import com.dental.ai.vo.AppointmentGuideVO;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.dental.department.dto.DepartmentQueryDTO;
import com.dental.department.service.DepartmentService;
import com.dental.department.vo.DepartmentVO;
import com.dental.doctor.dto.DoctorQueryDTO;
import com.dental.doctor.service.DoctorService;
import com.dental.doctor.vo.DoctorVO;
import com.dental.schedule.dto.ScheduleFilterDTO;
import com.dental.schedule.service.ScheduleService;
import com.dental.schedule.vo.ScheduleVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 示例业务：模型提取筛选条件，现有业务服务查询真实医生和可预约号源。 */
@Service
public class AppointmentGuideService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_CATALOG_PAGES = 10;
    private static final int MAX_SCHEDULE_PAGES = 100;
    private static final int MAX_CANDIDATES = 5;
    private static final int MAX_SLOTS_PER_DOCTOR = 3;
    private static final Set<String> TIME_PERIODS = Set.of("ANY", "MORNING", "AFTERNOON", "EVENING");
    private final AiService aiService;
    private final DepartmentService departmentService;
    private final DoctorService doctorService;
    private final ScheduleService scheduleService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AppointmentGuideService(AiService aiService, DepartmentService departmentService,
            DoctorService doctorService, ScheduleService scheduleService, ObjectMapper objectMapper,
            @Qualifier("aiClock") Clock clock) {
        this.aiService = aiService;
        this.departmentService = departmentService;
        this.doctorService = doctorService;
        this.scheduleService = scheduleService;
        this.objectMapper = objectMapper;
        this.clock = clock.withZone(TimeUtil.CLINIC_ZONE);
    }

    public AppointmentGuideVO guide(AppointmentGuideDTO input) {
        List<DepartmentVO> departments = catalog(page -> departmentService.list(
                new DepartmentQueryDTO(null, null, page, PAGE_SIZE), true));
        List<DoctorVO> doctors = catalog(page -> doctorService.list(
                new DoctorQueryDTO(null, null, null, page, PAGE_SIZE), true));
        validateContext(input, departments, doctors);
        if (doctors.isEmpty() || departments.isEmpty()) {
            return empty("诊所暂未发布医生或科室，请稍后再试", false);
        }
        // 只传公开目录，不传就诊人、电话、微信身份或医生后台账号信息。
        Map<String, Object> variables = Map.of(
                "today", LocalDate.now(clock).toString(),
                "message", input.message().trim(),
                "departmentId", input.departmentId() == null ? "" : input.departmentId().toString(),
                "doctorId", input.doctorId() == null ? "" : input.doctorId().toString(),
                "departments", json(departments.stream().map(department -> Map.of(
                        "id", department.id(), "name", department.name(),
                        "description", text(department.description(), 300))).toList()),
                "doctors", json(doctors.stream().map(doctor -> Map.of(
                        "id", doctor.id(), "name", doctor.name(), "departmentId", doctor.departmentId(),
                        "specialty", text(doctor.specialty(), 300))).toList()));
        AppointmentIntentDTO intent = aiService.generateStructured("appointment-intent", variables,
                AppointmentIntentDTO.class);
        return recommend(input, intent, departments, doctors);
    }

    /** 聊天模块已解析条件时复用真实号源查询，不再次请求模型。 */
    public AppointmentGuideVO recommend(AppointmentGuideDTO input, AppointmentIntentDTO intent) {
        List<DepartmentVO> departments = catalog(page -> departmentService.list(
                new DepartmentQueryDTO(null, null, page, PAGE_SIZE), true));
        List<DoctorVO> doctors = catalog(page -> doctorService.list(
                new DoctorQueryDTO(null, null, null, page, PAGE_SIZE), true));
        validateContext(input, departments, doctors);
        if (departments.isEmpty() || doctors.isEmpty()) {
            return empty("诊所暂未发布相关医生或科室，请联系诊所确认", false);
        }
        return recommend(input, intent, departments, doctors);
    }

    private AppointmentGuideVO recommend(AppointmentGuideDTO input, AppointmentIntentDTO intent,
            List<DepartmentVO> departments, List<DoctorVO> doctors) {
        if (intent == null || intent.supported() == null) {
            throw invalidIntent();
        }
        if (!intent.supported()) {
            return empty("请补充预约目的、希望就诊的日期或医生姓名；也可以手动选择科室和医生", true);
        }
        validateIntent(intent, departments, doctors);
        LocalDate today = LocalDate.now(clock);
        LocalDate from = intent.dateFrom() == null
                ? (intent.dateTo() == null ? today : intent.dateTo()) : intent.dateFrom();
        LocalDate to = intent.dateTo() == null
                ? (intent.dateFrom() == null ? from.plusDays(13) : from) : intent.dateTo();
        if (from.isBefore(today) || to.isBefore(from)) {
            throw BusinessException.bad("请选择今天或之后的日期，结束日期不得早于开始日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > 30 || to.isAfter(today.plusDays(90))) {
            throw BusinessException.bad("请查询未来 90 天内、跨度不超过 31 天的预约时间");
        }
        String timeOfDay = intent.timeOfDay() == null ? "ANY" : intent.timeOfDay();
        List<Integer> weekdays = intent.weekdays() == null ? List.of() : List.copyOf(intent.weekdays());
        List<DoctorVO> matchingDoctors = doctors.stream()
                .filter(doctor -> input.doctorId() == null || input.doctorId().equals(doctor.id()))
                .filter(doctor -> input.departmentId() == null || input.departmentId().equals(doctor.departmentId()))
                .filter(doctor -> intent.doctorIds() == null || intent.doctorIds().isEmpty()
                        || intent.doctorIds().contains(doctor.id()))
                .filter(doctor -> intent.departmentIds() == null || intent.departmentIds().isEmpty()
                        || intent.departmentIds().contains(doctor.departmentId()))
                .toList();
        Map<Long, DoctorVO> doctorById = matchingDoctors.stream()
                .collect(Collectors.toMap(DoctorVO::id, doctor -> doctor));
        ScheduleFilterDTO filter = new ScheduleFilterDTO();
        filter.setDateFrom(from);
        filter.setDateTo(to);
        filter.setSize(PAGE_SIZE);
        filter.setDoctorId(input.doctorId());
        filter.setDepartmentId(input.departmentId());
        Map<Long, List<ScheduleVO>> slots = new LinkedHashMap<>();
        boolean moreAvailable = false;
        if (!doctorById.isEmpty()) {
            for (int page = 1; page <= MAX_SCHEDULE_PAGES; page++) {
                filter.setPage(page);
                PageResult<ScheduleVO> result = scheduleService.publicList(filter);
                for (ScheduleVO schedule : result.records()) {
                    if (!doctorById.containsKey(schedule.doctorId()) || !matches(schedule, from, to, timeOfDay, weekdays)) {
                        continue;
                    }
                    if (!slots.containsKey(schedule.doctorId()) && slots.size() >= MAX_CANDIDATES) {
                        moreAvailable = true;
                        continue;
                    }
                    List<ScheduleVO> doctorSlots = slots.computeIfAbsent(schedule.doctorId(), key -> new ArrayList<>());
                    if (doctorSlots.size() < MAX_SLOTS_PER_DOCTOR) {
                        doctorSlots.add(schedule);
                    } else {
                        moreAvailable = true;
                    }
                }
                if (result.records().isEmpty() || (long) page * PAGE_SIZE >= result.total()) {
                    break;
                }
                if (page == MAX_SCHEDULE_PAGES) {
                    moreAvailable = true;
                }
            }
        }
        List<AppointmentGuideVO.Candidate> candidates = slots.entrySet().stream().map(entry -> {
            DoctorVO doctor = doctorById.get(entry.getKey());
            return new AppointmentGuideVO.Candidate(doctor.id(), doctor.name(), doctor.title(),
                    doctor.departmentId(), doctor.departmentName(), doctor.specialty(), List.copyOf(entry.getValue()));
        }).toList();
        String message = candidates.isEmpty()
                ? "当前条件下暂无可预约号源，可以调整日期或科室后再试"
                : "已找到符合条件的医生和时段，请核对后选择预约；号源以提交时为准";
        return new AppointmentGuideVO(message, false, intent.purpose().trim(), from, to, timeOfDay, weekdays,
                candidates, moreAvailable);
    }

    private boolean matches(ScheduleVO schedule, LocalDate from, LocalDate to,
            String timeOfDay, List<Integer> weekdays) {
        if (!"PUBLISHED".equals(schedule.status()) || schedule.remainingSlots() == null
                || schedule.remainingSlots() <= 0 || schedule.startTime() == null) {
            return false;
        }
        int cutoff = schedule.cancelBeforeMinutes() == null ? 0 : schedule.cancelBeforeMinutes();
        if (!schedule.startTime().toInstant().isAfter(clock.instant().plusSeconds(cutoff * 60L))) {
            return false;
        }
        ZonedDateTime start = schedule.startTime().atZoneSameInstant(TimeUtil.CLINIC_ZONE);
        if (start.toLocalDate().isBefore(from) || start.toLocalDate().isAfter(to)
                || (!weekdays.isEmpty() && !weekdays.contains(start.getDayOfWeek().getValue()))) {
            return false;
        }
        int hour = start.getHour();
        return switch (timeOfDay) {
            case "MORNING" -> hour < 12;
            case "AFTERNOON" -> hour >= 12 && hour < 18;
            case "EVENING" -> hour >= 18;
            default -> true;
        };
    }

    private void validateContext(AppointmentGuideDTO input, List<DepartmentVO> departments, List<DoctorVO> doctors) {
        if (input.departmentId() != null && departments.stream().noneMatch(d -> input.departmentId().equals(d.id()))) {
            throw BusinessException.bad("所选科室不存在或已停用，请刷新列表");
        }
        if (input.doctorId() != null && doctors.stream().noneMatch(d -> input.doctorId().equals(d.id())
                && (input.departmentId() == null || input.departmentId().equals(d.departmentId())))) {
            throw BusinessException.bad("所选医生不存在或已停用，请刷新列表");
        }
    }

    private void validateIntent(AppointmentIntentDTO intent, List<DepartmentVO> departments, List<DoctorVO> doctors) {
        Set<Long> departmentIds = departments.stream().map(DepartmentVO::id).collect(Collectors.toSet());
        Set<Long> doctorIds = doctors.stream().map(DoctorVO::id).collect(Collectors.toSet());
        if (intent.purpose() == null || intent.purpose().isBlank() || intent.purpose().length() > 80
                || (intent.timeOfDay() != null && !TIME_PERIODS.contains(intent.timeOfDay()))
                || (intent.departmentIds() != null && !departmentIds.containsAll(intent.departmentIds()))
                || (intent.doctorIds() != null && !doctorIds.containsAll(intent.doctorIds()))
                || (intent.weekdays() != null && intent.weekdays().stream()
                .anyMatch(day -> day == null || day < 1 || day > 7))) {
            throw invalidIntent();
        }
    }

    private <T> List<T> catalog(IntFunction<PageResult<T>> query) {
        List<T> records = new ArrayList<>();
        for (int page = 1; page <= MAX_CATALOG_PAGES; page++) {
            PageResult<T> result = query.apply(page);
            records.addAll(result.records());
            if (result.records().isEmpty() || (long) page * PAGE_SIZE >= result.total()) {
                return records;
            }
        }
        throw new BusinessException("AI_CATALOG_TOO_LARGE", "当前目录较大，请先手动选择科室和医生",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("公开目录序列化失败");
        }
    }

    private String text(String value, int maxLength) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), maxLength));
    }

    private BusinessException invalidIntent() {
        return new BusinessException("AI_INVALID_RESPONSE", "暂未能准确理解需求，请补充预约目的和时间后重试",
                HttpStatus.BAD_GATEWAY);
    }

    private AppointmentGuideVO empty(String message, boolean clarify) {
        return new AppointmentGuideVO(message, clarify, "", null, null, "ANY", List.of(), List.of(), false);
    }
}
