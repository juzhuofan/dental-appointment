package com.dental.appointment.vo;

import java.util.List;

public record DashboardVO(
        long todayAppointments,
        long pendingAppointments,
        long totalPatients,
        long totalDoctors,
        List<StatusCountVO> statusCounts,
        List<DailyTrendVO> dailyTrend) {
}
