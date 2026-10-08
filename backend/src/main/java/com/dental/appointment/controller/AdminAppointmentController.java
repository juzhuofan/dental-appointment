package com.dental.appointment.controller;

import com.dental.appointment.dto.AppointmentFilterDTO;
import com.dental.appointment.dto.AppointmentStatusDTO;
import com.dental.appointment.service.AppointmentService;
import com.dental.appointment.vo.AppointmentVO;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/appointments")
public class AdminAppointmentController {

    private final AppointmentService appointmentService;

    public AdminAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public R<PageResult<AppointmentVO>> list(@ModelAttribute AppointmentFilterDTO filter) {
        return R.ok(appointmentService.adminList(filter));
    }

    @PatchMapping("/{id}/status")
    public R<AppointmentVO> status(@PathVariable long id,
                                   @Valid @RequestBody R<@Valid AppointmentStatusDTO> request) {
        AppointmentStatusDTO input = required(request);
        return R.ok(appointmentService.changeStatus(id, input.getStatus(), input.getReason()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable long id) {
        appointmentService.delete(id);
        return R.ok(null);
    }

    private static <T> T required(R<T> request) {
        if (request == null || request.getData() == null) {
            throw BusinessException.bad("请求数据不能为空");
        }
        return request.getData();
    }
}
