package com.dental.leave.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.dental.common.BusinessException;
import com.dental.common.GlobalExceptionHandler;
import com.dental.common.PageResult;
import com.dental.leave.dto.DoctorLeaveApplyDTO;
import com.dental.leave.dto.DoctorLeaveFilterDTO;
import com.dental.leave.dto.DoctorLeaveReviewDTO;
import com.dental.leave.service.DoctorLeaveService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DoctorLeaveControllerTest {
    private final DoctorLeaveService service = mock(DoctorLeaveService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DoctorLeaveController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void listBindsPaginationAndStatus() throws Exception {
        when(service.list(any())).thenReturn(new PageResult<>(List.of(), 0, 2, 10));
        mvc.perform(get("/api/v1/admin/doctor-leaves?page=2&size=10&status=PENDING&doctorId=7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(2));
        verify(service).list(argThat((DoctorLeaveFilterDTO filter) -> filter.getPage() == 2
                && filter.getSize() == 10 && filter.getDoctorId() == 7L && "PENDING".equals(filter.getStatus())));
    }

    @Test
    void requestEnvelopeAndRequiredApplyFieldsAreValidated() throws Exception {
        for (String body : List.of("{}", "{\"data\":{}}", "{\"data\":{\"startTime\":\"2099-05-04T09:00:00+08:00\","
                + "\"endTime\":\"2099-05-04T13:00:00+08:00\",\"reason\":\" \"}}")) {
            mvc.perform(post("/api/v1/admin/doctor-leaves").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void applyAcceptsClinicOffsetAndDoesNotBindSubmittedDoctorId() throws Exception {
        mvc.perform(post("/api/v1/admin/doctor-leaves").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"doctorId\":999,\"startTime\":\"2099-05-04T09:00:00+08:00\","
                        + "\"endTime\":\"2099-05-04T13:00:00+08:00\",\"reason\":\"事假\"}}"))
                .andExpect(status().isOk());
        verify(service).apply(argThat((DoctorLeaveApplyDTO input) -> input.getStartTime().toInstant()
                .equals(java.time.Instant.parse("2099-05-04T01:00:00Z"))
                && input.getReason().equals("事假")));
    }

    @Test
    void appointmentImpactConflictIsReturnedWithCodeForSecondConfirmation() throws Exception {
        when(service.review(eq(8L), any())).thenThrow(BusinessException.conflict(
                "LEAVE_APPOINTMENT_CONFIRMATION_REQUIRED", "会取消 2 个未完成预约，请确认后再次审批"));
        mvc.perform(post("/api/v1/admin/doctor-leaves/8/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"approved\":true}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAVE_APPOINTMENT_CONFIRMATION_REQUIRED"));
        verify(service).review(eq(8L), argThat((DoctorLeaveReviewDTO input) -> !input.getConfirmCancelAppointments()));
    }

    @Test
    void explicitConfirmationAndBodylessWithdrawalUseAgreedRoutes() throws Exception {
        mvc.perform(post("/api/v1/admin/doctor-leaves/8/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"approved\":true,\"confirmCancelAppointments\":true}}"))
                .andExpect(status().isOk());
        verify(service).review(eq(8L), argThat((DoctorLeaveReviewDTO input) -> input.getConfirmCancelAppointments()));
        mvc.perform(post("/api/v1/admin/doctor-leaves/8/withdraw")).andExpect(status().isOk());
        verify(service).withdraw(8L);
    }
}
