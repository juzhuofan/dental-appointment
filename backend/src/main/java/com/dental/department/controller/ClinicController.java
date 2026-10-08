package com.dental.department.controller;

import com.dental.common.BusinessException;
import com.dental.common.R;
import com.dental.department.dto.ClinicDTO;
import com.dental.department.service.ClinicService;
import com.dental.department.vo.ClinicVO;
import com.dental.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 诊所资料公开查询与管理员维护接口。 */
@RestController
@RequestMapping("/api/v1")
public class ClinicController {

    private final ClinicService clinicService;

    public ClinicController(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping("/clinic")
    public R<ClinicVO> publicDetail() {
        return R.ok(clinicService.get());
    }

    @GetMapping("/admin/system-config")
    public R<ClinicVO> adminDetail() {
        requireAdmin();
        return R.ok(clinicService.get());
    }

    @PutMapping("/admin/system-config")
    public R<ClinicVO> update(@Valid @RequestBody R<ClinicDTO> request) {
        requireAdmin();
        return R.ok(clinicService.save(request.data()));
    }

    private void requireAdmin() {
        if (!CurrentUser.require().hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅管理员可维护诊所资料");
        }
    }
}
