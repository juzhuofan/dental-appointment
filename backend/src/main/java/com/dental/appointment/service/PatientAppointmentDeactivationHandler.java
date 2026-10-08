package com.dental.appointment.service;

import com.dental.user.service.UserDeactivationHandler;
import org.springframework.stereotype.Service;

import java.util.Set;

/** 患者账号失效前释放其所有活动预约占用的号源。 */
@Service
public class PatientAppointmentDeactivationHandler implements UserDeactivationHandler {

    private final AppointmentService appointmentService;

    public PatientAppointmentDeactivationHandler(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @Override
    public void beforeDeactivate(Long userId, Set<String> currentRoles) {
        if (currentRoles.contains("PATIENT")) {
            appointmentService.cancelActiveByPatient(userId);
        }
    }
}
