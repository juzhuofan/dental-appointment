package com.dental.appointment.controller;

import com.dental.appointment.service.AppointmentService;
import com.dental.appointment.vo.DashboardVO;
import com.dental.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {

    private final AppointmentService appointmentService;

    public DashboardController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public R<DashboardVO> dashboard() {
        return R.ok(appointmentService.dashboard());
    }
}
