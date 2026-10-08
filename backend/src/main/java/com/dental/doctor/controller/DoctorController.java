package com.dental.doctor.controller;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.doctor.dto.DoctorQueryDTO;
import com.dental.doctor.dto.DoctorSaveDTO;
import com.dental.doctor.service.DoctorService;
import com.dental.doctor.vo.DoctorVO;
import com.dental.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 医生公开查询与管理员维护接口。 */
@RestController
@RequestMapping("/api/v1")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping("/doctors")
    public R<PageResult<DoctorVO>> publicList(@RequestParam(required = false) Long departmentId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return R.ok(doctorService.list(new DoctorQueryDTO(departmentId, keyword, null, page, size), true));
    }

    @GetMapping("/doctors/{id}")
    public R<DoctorVO> publicDetail(@PathVariable long id) {
        return R.ok(doctorService.publicDetail(id));
    }

    @GetMapping("/admin/doctors")
    public R<PageResult<DoctorVO>> adminList(@RequestParam(required = false) Long departmentId,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        requireAdmin();
        return R.ok(doctorService.list(new DoctorQueryDTO(departmentId, keyword, status, page, size), false));
    }

    @PostMapping("/admin/doctors")
    public R<DoctorVO> create(@Valid @RequestBody R<DoctorSaveDTO> request) {
        requireAdmin();
        return R.ok(doctorService.create(request.data()));
    }

    @PutMapping("/admin/doctors/{id}")
    public R<DoctorVO> update(@PathVariable long id, @Valid @RequestBody R<DoctorSaveDTO> request) {
        requireAdmin();
        return R.ok(doctorService.update(id, request.data()));
    }

    @DeleteMapping("/admin/doctors/{id}")
    public R<Void> delete(@PathVariable long id) {
        requireAdmin();
        doctorService.delete(id);
        return R.ok(null);
    }

    private void requireAdmin() {
        if (!CurrentUser.require().hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅管理员可维护医生档案");
        }
    }
}
