package com.dental.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommonContractTest {

    private record ExampleRequest(@NotBlank String name) {
    }

    @Test
    void requestEnvelopeValidatesItsData() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new R<ExampleRequest>(null, null, null, null)).isEmpty());
            assertFalse(validator.validate(new R<>(null, null, new ExampleRequest(""), null)).isEmpty());
            assertTrue(validator.validate(new R<>(null, null, new ExampleRequest("patient"), null)).isEmpty());
        }
    }

    @Test
    void utcAndClinicTimeRoundTripWithoutDateShift() {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 6, 1, 30);
        OffsetDateTime clinic = TimeUtil.toClinic(utc);
        assertEquals("2026-10-06T09:30+08:00", clinic.toString());
        assertEquals(utc, TimeUtil.toUtc(clinic));
    }

    @Test
    void paginationAndErrorEnvelopeKeepSharedShape() {
        PageResult<String> page = new PageResult<>(List.of("entry"), 1, 1, 10);
        assertEquals(1, page.total());
        assertEquals("entry", page.records().get(0));
        assertEquals("DATA_CONFLICT", R.error("DATA_CONFLICT", "重复").getCode());
        assertEquals(409, BusinessException.conflict("重复").getHttpStatus());
    }
}
