package com.dental.web;

import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.service.AppointmentService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public R<Map<String, Object>> create(
            @Valid @RequestBody R<Requests.Appointment> request,
            @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return R.ok(appointments.create(request.data(), key));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public R<PageResult<Map<String, Object>>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return R.ok(appointments.mine(status, page, size));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable long id) {
        return R.ok(appointments.detail(id));
    }

    @PostMapping("/{id}/cancel")
    public R<Map<String, Object>> cancel(
            @PathVariable long id, @Valid @RequestBody R<Requests.Cancel> request) {
        return R.ok(appointments.cancel(id, request.data()));
    }
}
