package com.dental.common;

import static org.junit.jupiter.api.Assertions.*;

import com.dental.web.Requests;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

class BookingRulesTest {
    @Test
    void terminalStatesCannotTransitionOrOccupySlots() {
        for (var terminal :
                new AppointmentState[] {
                    AppointmentState.COMPLETED, AppointmentState.CANCELLED, AppointmentState.NO_SHOW
                }) {
            assertFalse(terminal.isActive());
            for (var target : AppointmentState.values()) {
                assertFalse(terminal.allows(target));
            }
        }
        assertTrue(AppointmentState.PENDING.allows(AppointmentState.CONFIRMED));
        assertFalse(AppointmentState.PENDING.allows(AppointmentState.COMPLETED));
        assertTrue(AppointmentState.CONFIRMED.allows(AppointmentState.COMPLETED));
    }

    @Test
    void envelopeCascadesValidationToDtoAndRejectsMissingData() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(
                    validator
                            .validate(
                                    new R<Requests.Login>(
                                            null, null, new Requests.Login("", ""), null))
                            .isEmpty());
            assertFalse(
                    validator.validate(new R<Requests.Login>(null, null, null, null)).isEmpty());
            assertTrue(
                    validator
                            .validate(
                                    new R<Requests.Login>(
                                            null,
                                            null,
                                            new Requests.Login("admin", "sample-password"),
                                            null))
                            .isEmpty());
        }
    }

    @Test
    void inclusiveClinicDateRangeConvertsToUtcBoundaries() {
        Map<String, Object> filter = DataValues.fields();
        DataValues.dateFilters(filter, "startTime", "2026-10-05", "2026-10-05");
        assertEquals(LocalDateTime.of(2026, 10, 4, 16, 0), filter.get("startTimeGe"));
        assertEquals(LocalDateTime.of(2026, 10, 5, 16, 0), filter.get("startTimeLt"));
    }
}
