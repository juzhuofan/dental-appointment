package com.dental.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dental.common.DataValues;
import com.dental.persistence.BusinessMapper;
import com.dental.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

class DashboardSerializationTest {
    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboardSerializesEmptyAndPopulatedAggregatesWithoutDateShift() throws Exception {
        var user = new CurrentUser(1, "admin", "演示管理员", List.of("ADMIN"), null, "unit-test");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        var mapper = mock(BusinessMapper.class);
        when(mapper.count(anyString(), anyMap(), isNull(), eq(false))).thenReturn(2L);
        var service =
                new AppointmentService(
                        mock(StoreService.class), mapper, mock(ScheduleService.class));
        var json =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        json.setTimeZone(TimeZone.getTimeZone("UTC"));
        when(mapper.statusCounts(isNull())).thenReturn(List.of());
        when(mapper.dailyTrend(isNull(), any(LocalDateTime.class))).thenReturn(List.of());
        assertTrue(
                json.readTree(json.writeValueAsString(service.dashboard()))
                        .get("dailyTrend")
                        .isEmpty());
        when(mapper.statusCounts(isNull()))
                .thenReturn(List.of(DataValues.fields("status", "PENDING", "count", 2L)));
        when(mapper.dailyTrend(isNull(), any(LocalDateTime.class)))
                .thenReturn(
                        List.of(
                                DataValues.fields(
                                        "date", java.sql.Date.valueOf("2026-10-05"), "count", 2L)));
        var populated = json.readTree(json.writeValueAsString(service.dashboard()));
        assertEquals("2026-10-05", populated.get("dailyTrend").get(0).get("date").asText());
        assertEquals(2, populated.get("statusCounts").get(0).get("count").asLong());
    }
}
