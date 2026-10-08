package com.dental.schedule.controller;

import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.schedule.dto.ScheduleFilterDTO;
import com.dental.schedule.vo.ScheduleVO;
import com.dental.schedule.service.ScheduleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public R<PageResult<ScheduleVO>> list(@ModelAttribute ScheduleFilterDTO filter) {
        return R.ok(scheduleService.publicList(filter));
    }

    @GetMapping("/{id}")
    public R<ScheduleVO> detail(@PathVariable long id) {
        return R.ok(scheduleService.publicDetail(id));
    }
}
