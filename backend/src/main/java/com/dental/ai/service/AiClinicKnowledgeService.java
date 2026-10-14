package com.dental.ai.service;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.department.dto.DepartmentQueryDTO;
import com.dental.department.service.ClinicService;
import com.dental.department.service.DepartmentService;
import com.dental.department.vo.ClinicVO;
import com.dental.department.vo.DepartmentVO;
import com.dental.doctor.dto.DoctorQueryDTO;
import com.dental.doctor.service.DoctorService;
import com.dental.doctor.vo.DoctorVO;
import com.dental.notice.dto.NoticeQueryDTO;
import com.dental.notice.service.NoticeService;
import com.dental.notice.vo.NoticeVO;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 每轮读取最新公开事实，仅通过业务接口读取，不访问患者或后台账号资料。 */
@Service
public class AiClinicKnowledgeService {

    private final ClinicService clinicService;
    private final DepartmentService departmentService;
    private final DoctorService doctorService;
    private final NoticeService noticeService;

    public AiClinicKnowledgeService(ClinicService clinicService, DepartmentService departmentService,
            DoctorService doctorService, NoticeService noticeService) {
        this.clinicService = clinicService;
        this.departmentService = departmentService;
        this.doctorService = doctorService;
        this.noticeService = noticeService;
    }

    public Snapshot load() {
        return new Snapshot(clinicService.get(),
                all(page -> departmentService.list(new DepartmentQueryDTO(null, null, page, 100), true)),
                all(page -> doctorService.list(new DoctorQueryDTO(null, null, null, page, 100), true)),
                noticeService.list(new NoticeQueryDTO(null, null, 1, 5), true).records());
    }

    public ClinicVO clinic() {
        return clinicService.get();
    }

    public ModelKnowledge modelKnowledge(Snapshot snapshot) {
        ClinicVO clinic = snapshot.clinic();
        ClinicVO brief = new ClinicVO(clinic.name(), clinic.phone(), clinic.address(), clinic.openingHours(),
                brief(clinic.introduction(), 1500), clinic.cancelBeforeMinutes());
        return new ModelKnowledge(brief,
                snapshot.departments().stream().map(item -> new PublicDepartment(item.id(), item.name(),
                        brief(item.description(), 200))).toList(),
                snapshot.doctors().stream().map(item -> new PublicDoctor(item.id(), item.name(),
                        item.departmentId(), item.departmentName(), item.title(), brief(item.specialty(), 300),
                        brief(item.introduction(), 300))).toList(),
                snapshot.notices().stream().map(item -> new PublicNotice(item.id(), item.title(),
                        brief(item.content(), 500))).toList());
    }

    private <T> List<T> all(IntFunction<PageResult<T>> query) {
        List<T> records = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            PageResult<T> result = query.apply(page);
            records.addAll(result.records());
            if (result.records().isEmpty() || (long) page * 100 >= result.total()) {
                return List.copyOf(records);
            }
        }
        throw new BusinessException("AI_CATALOG_TOO_LARGE", "当前目录较大，请手动选择科室或医生",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private String brief(String text, int length) {
        return text == null ? "" : text.substring(0, Math.min(text.length(), length));
    }

    public record Snapshot(ClinicVO clinic, List<DepartmentVO> departments,
            List<DoctorVO> doctors, List<NoticeVO> notices) { }
    public record ModelKnowledge(ClinicVO clinic, List<PublicDepartment> departments,
            List<PublicDoctor> doctors, List<PublicNotice> notices) { }
    public record PublicDepartment(Long id, String name, String description) { }
    public record PublicDoctor(Long id, String name, Long departmentId, String departmentName,
            String title, String specialty, String introduction) { }
    public record PublicNotice(Long id, String title, String content) { }
}
