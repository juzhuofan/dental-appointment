package com.dental.web;

import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.service.*;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final CatalogService catalog;
    private final ScheduleService schedules;
    private final AppointmentService appointments;
    private final UserService users;

    public AdminController(
            CatalogService catalog,
            ScheduleService schedules,
            AppointmentService appointments,
            UserService users) {
        this.catalog = catalog;
        this.schedules = schedules;
        this.appointments = appointments;
        this.users = users;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public R<Map<String, Object>> dashboard() {
        return R.ok(appointments.dashboard());
    }

    @GetMapping("/departments")
    public R<PageResult<Map<String, Object>>> departments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return R.ok(catalog.departments(false, keyword, status, page, size));
    }

    @PostMapping("/departments")
    public R<Map<String, Object>> department(@Valid @RequestBody R<Requests.Department> request) {
        return R.ok(catalog.saveDepartment(null, request.data()));
    }

    @PutMapping("/departments/{id}")
    public R<Map<String, Object>> department(
            @PathVariable long id, @Valid @RequestBody R<Requests.Department> request) {
        return R.ok(catalog.saveDepartment(id, request.data()));
    }

    @DeleteMapping("/departments/{id}")
    public R<Map<String, Object>> deleteDepartment(@PathVariable long id) {
        catalog.deleteDepartment(id);
        return R.ok(Map.of());
    }

    @GetMapping("/doctors")
    public R<PageResult<Map<String, Object>>> doctors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return R.ok(catalog.doctors(false, departmentId, keyword, status, page, size));
    }

    @PostMapping("/doctors")
    public R<Map<String, Object>> doctor(@Valid @RequestBody R<Requests.Doctor> request) {
        return R.ok(catalog.saveDoctor(null, request.data()));
    }

    @PutMapping("/doctors/{id}")
    public R<Map<String, Object>> doctor(
            @PathVariable long id, @Valid @RequestBody R<Requests.Doctor> request) {
        return R.ok(catalog.saveDoctor(id, request.data()));
    }

    @DeleteMapping("/doctors/{id}")
    public R<Map<String, Object>> deleteDoctor(@PathVariable long id) {
        catalog.deleteDoctor(id);
        return R.ok(Map.of());
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public R<PageResult<Map<String, Object>>> schedules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String status) {
        return R.ok(
                schedules.list(
                        false, doctorId, departmentId, dateFrom, dateTo, status, page, size));
    }

    @PostMapping("/schedules")
    public R<Map<String, Object>> schedule(@Valid @RequestBody R<Requests.Schedule> request) {
        return R.ok(schedules.save(null, request.data()));
    }

    @PutMapping("/schedules/{id}")
    public R<Map<String, Object>> schedule(
            @PathVariable long id, @Valid @RequestBody R<Requests.Schedule> request) {
        return R.ok(schedules.save(id, request.data()));
    }

    @PatchMapping("/schedules/{id}/status")
    public R<Map<String, Object>> scheduleStatus(
            @PathVariable long id, @Valid @RequestBody R<Requests.ScheduleStatus> request) {
        return R.ok(schedules.status(id, request.data()));
    }

    @DeleteMapping("/schedules/{id}")
    public R<Map<String, Object>> deleteSchedule(@PathVariable long id) {
        schedules.delete(id);
        return R.ok(Map.of());
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public R<PageResult<Map<String, Object>>> appointments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String keyword) {
        return R.ok(
                appointments.adminList(
                        status, doctorId, departmentId, dateFrom, dateTo, keyword, page, size));
    }

    @PatchMapping("/appointments/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public R<Map<String, Object>> appointmentStatus(
            @PathVariable long id, @Valid @RequestBody R<Requests.AppointmentStatus> request) {
        return R.ok(appointments.status(id, request.data()));
    }

    @DeleteMapping("/appointments/{id}")
    public R<Map<String, Object>> deleteAppointment(@PathVariable long id) {
        appointments.delete(id);
        return R.ok(Map.of());
    }

    @GetMapping("/notices")
    public R<PageResult<Map<String, Object>>> notices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return R.ok(catalog.notices(false, keyword, status, page, size));
    }

    @PostMapping("/notices")
    public R<Map<String, Object>> notice(@Valid @RequestBody R<Requests.Notice> request) {
        return R.ok(catalog.saveNotice(null, request.data()));
    }

    @PutMapping("/notices/{id}")
    public R<Map<String, Object>> notice(
            @PathVariable long id, @Valid @RequestBody R<Requests.Notice> request) {
        return R.ok(catalog.saveNotice(id, request.data()));
    }

    @DeleteMapping("/notices/{id}")
    public R<Map<String, Object>> deleteNotice(@PathVariable long id) {
        catalog.deleteNotice(id);
        return R.ok(Map.of());
    }

    @GetMapping("/patients")
    public R<PageResult<Map<String, Object>>> patients(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return R.ok(users.patients(keyword, page, size));
    }

    @GetMapping("/users")
    public R<PageResult<Map<String, Object>>> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        return R.ok(users.users(keyword, role, status, page, size));
    }

    @PostMapping("/users")
    public R<Map<String, Object>> user(@Valid @RequestBody R<Requests.User> request) {
        return R.ok(users.saveUser(null, request.data()));
    }

    @PutMapping("/users/{id}")
    public R<Map<String, Object>> user(
            @PathVariable long id, @Valid @RequestBody R<Requests.User> request) {
        return R.ok(users.saveUser(id, request.data()));
    }

    @DeleteMapping("/users/{id}")
    public R<Map<String, Object>> deleteUser(@PathVariable long id) {
        users.deleteUser(id);
        return R.ok(Map.of());
    }

    @GetMapping("/operation-logs")
    public R<PageResult<Map<String, Object>>> operationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword) {
        return R.ok(users.operationLogs(action, keyword, page, size));
    }

    @GetMapping("/system-config")
    public R<Map<String, Object>> systemConfig() {
        return R.ok(catalog.clinicFromDatabase());
    }

    @PutMapping("/system-config")
    public R<Map<String, Object>> systemConfig(@Valid @RequestBody R<Requests.Clinic> request) {
        return R.ok(catalog.saveClinic(request.data()));
    }
}
