package com.dental.web;

import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.service.CatalogService;
import com.dental.service.ScheduleService;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PublicController {
    private final CatalogService catalog;
    private final ScheduleService schedules;

    public PublicController(CatalogService catalog, ScheduleService schedules) {
        this.catalog = catalog;
        this.schedules = schedules;
    }

    @GetMapping("/clinic")
    public R<Map<String, Object>> clinic() {
        return R.ok(catalog.clinic());
    }

    @GetMapping("/departments")
    public R<PageResult<Map<String, Object>>> departments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return R.ok(catalog.departments(true, keyword, null, page, size));
    }

    @GetMapping("/doctors")
    public R<PageResult<Map<String, Object>>> doctors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword) {
        return R.ok(catalog.doctors(true, departmentId, keyword, null, page, size));
    }

    @GetMapping("/doctors/{id}")
    public R<Map<String, Object>> doctor(@PathVariable long id) {
        return R.ok(catalog.doctor(id, true));
    }

    @GetMapping("/schedules")
    public R<PageResult<Map<String, Object>>> schedules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return R.ok(
                schedules.list(true, doctorId, departmentId, dateFrom, dateTo, null, page, size));
    }

    @GetMapping("/notices")
    public R<PageResult<Map<String, Object>>> notices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(catalog.notices(true, null, null, page, size));
    }

    @GetMapping("/schedules/{id}")
    public R<Map<String, Object>> schedule(@PathVariable long id) {
        return R.ok(schedules.detail(id));
    }
}
