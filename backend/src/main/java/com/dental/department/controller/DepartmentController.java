package com.dental.department.controller;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.department.dto.DepartmentQueryDTO;
import com.dental.department.dto.DepartmentSaveDTO;
import com.dental.department.service.DepartmentService;
import com.dental.department.vo.DepartmentVO;
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

/** 科室公开查询与管理员维护接口。 */
@RestController
@RequestMapping("/api/v1")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/departments")
    public R<PageResult<DepartmentVO>> publicList(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return R.ok(departmentService.list(new DepartmentQueryDTO(null, null, page, size), true));
    }

    @GetMapping("/admin/departments")
    public R<PageResult<DepartmentVO>> adminList(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        requireAdmin();
        return R.ok(departmentService.list(new DepartmentQueryDTO(keyword, status, page, size), false));
    }

    @PostMapping("/admin/departments")
    public R<DepartmentVO> create(@Valid @RequestBody R<DepartmentSaveDTO> request) {
        requireAdmin();
        return R.ok(departmentService.create(request.data()));
    }

    @PutMapping("/admin/departments/{id}")
    public R<DepartmentVO> update(@PathVariable long id, @Valid @RequestBody R<DepartmentSaveDTO> request) {
        requireAdmin();
        return R.ok(departmentService.update(id, request.data()));
    }

    @DeleteMapping("/admin/departments/{id}")
    public R<Void> delete(@PathVariable long id) {
        requireAdmin();
        departmentService.delete(id);
        return R.ok(null);
    }

    private void requireAdmin() {
        if (!CurrentUser.require().hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅管理员可维护科室");
        }
    }
}
