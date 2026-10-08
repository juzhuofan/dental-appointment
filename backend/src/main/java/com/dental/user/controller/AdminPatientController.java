package com.dental.user.controller;

import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.user.service.UserService;
import com.dental.user.vo.PatientProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员查看脱敏后的就诊人资料。 */
@RestController
@RequestMapping("/api/v1/admin/patients")
public class AdminPatientController {
    private final UserService userService;

    public AdminPatientController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public R<PageResult<PatientProfileVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return R.ok(userService.listPatients(keyword, page, size));
    }
}
