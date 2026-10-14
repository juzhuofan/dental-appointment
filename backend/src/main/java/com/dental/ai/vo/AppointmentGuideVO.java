package com.dental.ai.vo;

import com.dental.schedule.vo.ScheduleVO;
import java.time.LocalDate;
import java.util.List;

/** 医生信息、时间和余量均由数据库查询得到。 */
public record AppointmentGuideVO(
        String message,
        boolean needsClarification,
        String purpose,
        LocalDate dateFrom,
        LocalDate dateTo,
        String timeOfDay,
        List<Integer> weekdays,
        List<Candidate> candidates,
        boolean moreAvailable) {

    public record Candidate(Long doctorId, String doctorName, String title, Long departmentId,
            String departmentName, String specialty, List<ScheduleVO> schedules) { }
}
