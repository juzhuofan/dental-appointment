package com.dental.appointment.controller;

import com.dental.appointment.dto.AppointmentCancelDTO;
import com.dental.appointment.dto.AppointmentCreateDTO;
import com.dental.appointment.dto.AppointmentFilterDTO;
import com.dental.appointment.service.AppointmentService;
import com.dental.appointment.vo.AppointmentVO;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public R<AppointmentVO> create(@Valid @RequestBody R<@Valid AppointmentCreateDTO> request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return R.ok(appointmentService.create(required(request), idempotencyKey));
    }

    @GetMapping("/me")
    public R<PageResult<AppointmentVO>> myList(@ModelAttribute AppointmentFilterDTO filter) {
        return R.ok(appointmentService.myList(filter));
    }

    @GetMapping("/{id}")
    public R<AppointmentVO> detail(@PathVariable long id) {
        return R.ok(appointmentService.detail(id));
    }

    @PostMapping("/{id}/cancel")
    public R<AppointmentVO> cancel(@PathVariable long id,
                                   @Valid @RequestBody R<@Valid AppointmentCancelDTO> request) {
        return R.ok(appointmentService.cancel(id, required(request).getReason()));
    }

    private static <T> T required(R<T> request) {
        if (request == null || request.getData() == null) {
            throw BusinessException.bad("请求数据不能为空");
        }
        return request.getData();
    }
}
