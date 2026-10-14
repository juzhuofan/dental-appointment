package com.dental.appointment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dental.appointment.dto.AppointmentCreateDTO;
import com.dental.appointment.entity.AppointmentEntity;
import com.dental.appointment.entity.AppointmentIdempotencyEntity;
import com.dental.appointment.mapper.AppointmentIdempotencyMapper;
import com.dental.appointment.mapper.AppointmentMapper;
import com.dental.appointment.vo.BookableScheduleVO;
import com.dental.appointment.vo.PatientSnapshotVO;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import com.dental.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 真实创建服务的资料校验、占号边界与已完成请求重放；不连接数据库或外部接口。 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceCreateTest {

    private static final long USER_ID = 41L;
    private static final long SCHEDULE_ID = 101L;
    private static final long PROFILE_ID = 202L;
    private static final long APPOINTMENT_ID = 303L;

    @Mock private AppointmentMapper appointments;
    @Mock private AppointmentIdempotencyMapper idempotency;
    @Mock private OperationLogService audit;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointments, idempotency, audit);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(USER_ID, "wechat", "微信用户", List.of("PATIENT"), null, "test"), null, List.of()));
        when(appointments.lockActiveUser(USER_ID)).thenReturn(1);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "拒绝不完整就诊资料 #{index}")
    @MethodSource("incompleteProfiles")
    void rejectsIncompleteProfileBeforeOccupyingSlot(String name, String phone) {
        when(appointments.lockScheduleForBooking(SCHEDULE_ID)).thenReturn(schedule());
        when(appointments.findPatientProfile(PROFILE_ID, USER_ID)).thenReturn(patient(name, phone));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.create(input(), null));

        assertEquals("PATIENT_PROFILE_INCOMPLETE", failure.getCode());
        assertEquals(409, failure.getHttpStatus());
        verify(appointments, never()).reserveSlot(anyLong(), any());
        verify(appointments, never()).insert(any(AppointmentEntity.class));
        verify(audit, never()).record(any(), any(), any(), any());
    }

    static Stream<Arguments> incompleteProfiles() {
        return Stream.of(
                Arguments.of(null, "13800000000"),
                Arguments.of("", "13800000000"),
                Arguments.of(" \t\n", "13800000000"),
                Arguments.of("\u3000", "13800000000"),
                Arguments.of("张".repeat(81), "13800000000"),
                Arguments.of("张三", null),
                Arguments.of("张三", ""),
                Arguments.of("张三", "1380000000"),
                Arguments.of("张三", "12800000000"),
                Arguments.of("张三", "1380000000a"),
                Arguments.of("张三", "138000000000"),
                Arguments.of("张三", " 13800000000"));
    }

    @Test
    void validProfileCreatesBookingWithTrimmedNameAfterValidation() {
        when(appointments.lockScheduleForBooking(SCHEDULE_ID)).thenReturn(schedule());
        when(appointments.findPatientProfile(PROFILE_ID, USER_ID)).thenReturn(patient("  张三  ", "13800000000"));
        when(appointments.reserveSlot(eq(SCHEDULE_ID), any())).thenReturn(1);
        AtomicReference<AppointmentEntity> inserted = new AtomicReference<>();
        when(appointments.insert(any(AppointmentEntity.class))).thenAnswer(call -> {
            AppointmentEntity entity = call.getArgument(0);
            entity.setId(APPOINTMENT_ID);
            inserted.set(entity);
            return 1;
        });
        when(appointments.findDetail(APPOINTMENT_ID)).thenAnswer(call -> inserted.get());

        var result = service.create(input(), null);

        assertEquals(APPOINTMENT_ID, result.id());
        assertEquals("张三", result.patientName());
        assertEquals("13800000000", result.patientPhone());
        assertEquals("PENDING", result.status());
        assertEquals(PROFILE_ID + ":" + SCHEDULE_ID, inserted.get().getAppointmentActiveKey());
        var order = inOrder(appointments);
        order.verify(appointments).findPatientProfile(PROFILE_ID, USER_ID);
        order.verify(appointments).reserveSlot(eq(SCHEDULE_ID), any());
        order.verify(appointments).insert(any(AppointmentEntity.class));
    }

    @Test
    void acceptsNameAtMaximumLengthAfterTrimming() {
        when(appointments.lockScheduleForBooking(SCHEDULE_ID)).thenReturn(schedule());
        when(appointments.findPatientProfile(PROFILE_ID, USER_ID)).thenReturn(patient(" " + "张".repeat(80) + " ", "13900000000"));
        when(appointments.reserveSlot(eq(SCHEDULE_ID), any())).thenReturn(1);
        AtomicReference<AppointmentEntity> inserted = new AtomicReference<>();
        when(appointments.insert(any(AppointmentEntity.class))).thenAnswer(call -> {
            AppointmentEntity entity = call.getArgument(0);
            entity.setId(APPOINTMENT_ID);
            inserted.set(entity);
            return 1;
        });
        when(appointments.findDetail(APPOINTMENT_ID)).thenAnswer(call -> inserted.get());

        assertEquals(80, service.create(input(), null).patientName().length());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CONFIRMED", "CANCELLED", "COMPLETED", "NO_SHOW"})
    void completedIdempotentRequestReplaysCurrentBookingWithoutRevalidatingChangedData(String status) {
        AppointmentIdempotencyEntity reservation = new AppointmentIdempotencyEntity();
        reservation.setId(404L);
        reservation.setAppointmentId(APPOINTMENT_ID);
        when(idempotency.reserve(eq(USER_ID), eq("existing-request"), any(), any())).thenAnswer(call -> {
            reservation.setRequestHash(call.getArgument(2));
            return 0;
        });
        when(idempotency.lock(USER_ID, "existing-request")).thenReturn(reservation);
        AppointmentEntity previous = new AppointmentEntity();
        previous.setId(APPOINTMENT_ID);
        previous.setStatus(status);
        when(appointments.findDetail(APPOINTMENT_ID)).thenReturn(previous);

        var result = service.create(input(), "existing-request");

        assertEquals(APPOINTMENT_ID, result.id());
        assertEquals(status, result.status());
        verify(appointments, never()).lockScheduleForBooking(anyLong());
        verify(appointments, never()).findPatientProfile(anyLong(), anyLong());
        verify(appointments, never()).reserveSlot(anyLong(), any());
        verify(appointments, never()).insert(any(AppointmentEntity.class));
    }

    private static AppointmentCreateDTO input() {
        AppointmentCreateDTO input = new AppointmentCreateDTO();
        input.setScheduleId(SCHEDULE_ID);
        input.setPatientProfileId(PROFILE_ID);
        return input;
    }

    private static PatientSnapshotVO patient(String name, String phone) {
        PatientSnapshotVO patient = new PatientSnapshotVO();
        patient.setId(PROFILE_ID);
        patient.setRealName(name);
        patient.setPhone(phone);
        return patient;
    }

    private static BookableScheduleVO schedule() {
        BookableScheduleVO schedule = new BookableScheduleVO();
        schedule.setId(SCHEDULE_ID);
        schedule.setDoctorId(10L);
        schedule.setDepartmentId(11L);
        schedule.setDoctorName("测试医生");
        schedule.setDepartmentName("测试科室");
        schedule.setDoctorStatus(1);
        schedule.setDepartmentStatus(1);
        schedule.setStatus("PUBLISHED");
        schedule.setStartTime(LocalDateTime.now(ZoneOffset.UTC).plusDays(3));
        schedule.setEndTime(schedule.getStartTime().plusHours(1));
        schedule.setTotalSlots(5);
        schedule.setBookedSlots(0);
        schedule.setCancelBeforeMinutes(120);
        return schedule;
    }
}
