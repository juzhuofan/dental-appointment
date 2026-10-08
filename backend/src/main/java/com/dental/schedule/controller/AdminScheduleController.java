package com.dental.schedule.controller;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.schedule.dto.ScheduleFilterDTO;
import com.dental.schedule.dto.ScheduleSaveDTO;
import com.dental.schedule.dto.ScheduleStatusDTO;
import com.dental.schedule.service.ScheduleService;
import com.dental.schedule.vo.ScheduleVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/schedules")
public class AdminScheduleController {

    private final ScheduleService scheduleService;

    public AdminScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public R<PageResult<ScheduleVO>> list(@ModelAttribute ScheduleFilterDTO filter) {
        return R.ok(scheduleService.adminList(filter));
    }

    @PostMapping
    public R<ScheduleVO> create(@Valid @RequestBody R<@Valid ScheduleSaveDTO> request) {
        return R.ok(scheduleService.create(required(request)));
    }

    @PutMapping("/{id}")
    public R<ScheduleVO> update(@PathVariable long id, @Valid @RequestBody R<@Valid ScheduleSaveDTO> request) {
        return R.ok(scheduleService.update(id, required(request)));
    }

    @PatchMapping("/{id}/status")
    public R<ScheduleVO> status(@PathVariable long id, @Valid @RequestBody R<@Valid ScheduleStatusDTO> request) {
        return R.ok(scheduleService.changeStatus(id, required(request).getStatus()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable long id) {
        scheduleService.delete(id);
        return R.ok(null);
    }

    private static <T> T required(R<T> request) {
        if (request == null || request.getData() == null) {
            throw BusinessException.bad("请求数据不能为空");
        }
        return request.getData();
    }
}
