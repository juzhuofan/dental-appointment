package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dental.ai.dto.AppointmentGuideDTO;
import com.dental.ai.dto.AppointmentIntentDTO;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.department.service.DepartmentService;
import com.dental.department.vo.DepartmentVO;
import com.dental.doctor.service.DoctorService;
import com.dental.doctor.vo.DoctorVO;
import com.dental.schedule.dto.ScheduleFilterDTO;
import com.dental.schedule.service.ScheduleService;
import com.dental.schedule.vo.ScheduleVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppointmentGuideServiceTest {

    private final AiService ai = mock(AiService.class);
    private final DepartmentService departments = mock(DepartmentService.class);
    private final DoctorService doctors = mock(DoctorService.class);
    private final ScheduleService schedules = mock(ScheduleService.class);
    private AppointmentGuideService service;

    @BeforeEach
    void setUp() {
        when(departments.list(any(), eq(true))).thenReturn(new PageResult<>(List.of(
                new DepartmentVO(1L, "牙周科", "洗牙", null, null, 1, 1),
                new DepartmentVO(2L, "儿童科", "儿童检查", null, null, 2, 1)), 2, 1, 100));
        when(doctors.list(any(), eq(true))).thenReturn(new PageResult<>(List.of(
                new DoctorVO(101L, 901L, 1L, "牙周科", "医生甲", "医师", "洗牙", "", "", null, 1),
                new DoctorVO(102L, 902L, 2L, "儿童科", "医生乙", "医师", "儿童检查", "", "", null, 1)), 2, 1, 100));
        service = new AppointmentGuideService(ai, departments, doctors, schedules,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-10-09T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void filtersWeekendsAfternoonsFullClosedAndUnrelatedSlots() {
        givenIntent(new AppointmentIntentDTO(true, "洗牙", List.of(1L), List.of(),
                null, null, "AFTERNOON", List.of(6, 7), ""));
        givenSchedules(List.of(slot(1, 101, "2026-10-10T14:00:00+08:00", 2, "PUBLISHED"),
                slot(2, 101, "2026-10-10T09:00:00+08:00", 2, "PUBLISHED"),
                slot(3, 101, "2026-10-12T14:00:00+08:00", 2, "PUBLISHED"),
                slot(4, 101, "2026-10-11T14:00:00+08:00", 0, "PUBLISHED"),
                slot(5, 101, "2026-10-11T14:00:00+08:00", 2, "CANCELLED"),
                slot(6, 102, "2026-10-11T14:00:00+08:00", 2, "PUBLISHED")));
        var result = service.guide(new AppointmentGuideDTO("周末下午洗牙", null, null));
        assertEquals(1, result.candidates().size());
        assertEquals(1L, result.candidates().get(0).schedules().get(0).id());
        assertEquals(LocalDate.parse("2026-10-09"), result.dateFrom());
        assertEquals(LocalDate.parse("2026-10-22"), result.dateTo());
    }

    @Test
    void rejectsHallucinatedIdsWithoutQueryingSchedules() {
        givenIntent(new AppointmentIntentDTO(true, "洗牙", List.of(999L), List.of(), null, null, "ANY", List.of(), ""));
        assertEquals("AI_INVALID_RESPONSE", assertThrows(BusinessException.class,
                () -> service.guide(new AppointmentGuideDTO("洗牙", null, null))).getCode());
        verifyNoInteractions(schedules);
    }

    @Test
    void preservesSelectedDoctorEvenWhenModelReturnsAnotherDoctor() {
        givenIntent(new AppointmentIntentDTO(true, "检查", List.of(), List.of(102L), null, null, "ANY", List.of(), ""));
        assertTrue(service.guide(new AppointmentGuideDTO("找乙医生", null, 101L)).candidates().isEmpty());
        verifyNoInteractions(schedules);
    }

    @Test
    void queriesLaterPagesWhenEarlySlotsAreFull() {
        givenIntent(new AppointmentIntentDTO(true, "洗牙", List.of(1L), List.of(), null, null, "ANY", List.of(), ""));
        when(schedules.publicList(any())).thenAnswer(invocation -> {
            ScheduleFilterDTO filter = invocation.getArgument(0);
            return filter.getPage() == 1
                    ? new PageResult<>(Collections.nCopies(100, slot(1, 101, "2026-10-10T09:00:00+08:00", 0, "PUBLISHED")), 101, 1, 100)
                    : new PageResult<>(List.of(slot(2, 101, "2026-10-11T14:00:00+08:00", 3, "PUBLISHED")), 101, 2, 100);
        });
        assertEquals(2L, service.guide(new AppointmentGuideDTO("洗牙", null, null))
                .candidates().get(0).schedules().get(0).id());
    }

    @Test
    void rejectsPastReversedAndOverlyWideDateRanges() {
        for (List<String> dates : List.of(List.of("2026-10-08", "2026-10-09"),
                List.of("2026-10-11", "2026-10-10"), List.of("2026-10-09", "2026-12-01"))) {
            givenIntent(new AppointmentIntentDTO(true, "检查", List.of(), List.of(), LocalDate.parse(dates.get(0)),
                    LocalDate.parse(dates.get(1)), "ANY", List.of(), ""));
            assertEquals("INVALID_ARGUMENT", assertThrows(BusinessException.class,
                    () -> service.guide(new AppointmentGuideDTO("检查", null, null))).getCode());
        }
        verifyNoInteractions(schedules);
    }

    @Test
    void returnsClarificationForUnsupportedRequest() {
        givenIntent(new AppointmentIntentDTO(false, "", List.of(), List.of(), null, null, "ANY", List.of(), "不显示模型原文"));
        var result = service.guide(new AppointmentGuideDTO("写程序", null, null));
        assertTrue(result.needsClarification());
        assertFalse(result.message().contains("不显示模型原文"));
        verifyNoInteractions(schedules);
    }

    @Test
    void removesSlotsPastBookingDeadline() {
        givenIntent(new AppointmentIntentDTO(true, "检查", List.of(), List.of(), null, null, "ANY", List.of(), ""));
        givenSchedules(List.of(slot(1, 101, "2026-10-09T11:00:00+08:00", 2, "PUBLISHED")));
        assertTrue(service.guide(new AppointmentGuideDTO("检查", null, null)).candidates().isEmpty());
    }

    @Test
    void sendsOnlyPublicCatalogFieldsToModel() {
        when(ai.generateStructured(eq("appointment-intent"), any(), eq(AppointmentIntentDTO.class)))
                .thenAnswer(invocation -> {
                    java.util.Map<String, Object> variables = invocation.getArgument(1);
                    assertFalse(variables.get("doctors").toString().contains("userId"));
                    assertFalse(variables.get("doctors").toString().contains("901"));
                    return new AppointmentIntentDTO(false, "", List.of(), List.of(), null, null, "ANY", List.of(), "");
                });
        service.guide(new AppointmentGuideDTO("检查", null, null));
    }

    private void givenIntent(AppointmentIntentDTO intent) {
        when(ai.generateStructured(eq("appointment-intent"), any(), eq(AppointmentIntentDTO.class))).thenReturn(intent);
    }

    private void givenSchedules(List<ScheduleVO> records) {
        when(schedules.publicList(any())).thenReturn(new PageResult<>(records, records.size(), 1, 100));
    }

    private ScheduleVO slot(long id, long doctorId, String start, int remaining, String status) {
        OffsetDateTime time = OffsetDateTime.parse(start);
        return new ScheduleVO(id, doctorId, "医生", doctorId == 101 ? 1L : 2L, "科室", time, time.plusHours(1),
                5, 5 - remaining, remaining, status, 120);
    }
}
