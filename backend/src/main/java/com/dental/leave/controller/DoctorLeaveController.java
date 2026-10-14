package com.dental.leave.controller;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.leave.dto.DoctorLeaveApplyDTO;
import com.dental.leave.dto.DoctorLeaveFilterDTO;
import com.dental.leave.dto.DoctorLeaveReviewDTO;
import com.dental.leave.service.DoctorLeaveService;
import com.dental.leave.vo.DoctorLeaveVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/doctor-leaves")
public class DoctorLeaveController {
    private final DoctorLeaveService service;

    public DoctorLeaveController(DoctorLeaveService service) { this.service = service; }

    @GetMapping
    public R<PageResult<DoctorLeaveVO>> list(@ModelAttribute DoctorLeaveFilterDTO filter) {
        return R.ok(service.list(filter));
    }

    @PostMapping
    public R<DoctorLeaveVO> apply(@Valid @RequestBody R<@Valid DoctorLeaveApplyDTO> request) {
        return R.ok(service.apply(required(request)));
    }

    @PostMapping("/{id}/review")
    public R<DoctorLeaveVO> review(@PathVariable long id,
                                  @Valid @RequestBody R<@Valid DoctorLeaveReviewDTO> request) {
        return R.ok(service.review(id, required(request)));
    }

    @PostMapping("/{id}/withdraw")
    public R<DoctorLeaveVO> withdraw(@PathVariable long id) {
        return R.ok(service.withdraw(id));
    }

    private static <T> T required(R<T> request) {
        if (request == null || request.getData() == null) {
            throw BusinessException.bad("请求数据不能为空");
        }
        return request.getData();
    }
}
