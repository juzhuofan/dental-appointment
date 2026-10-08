package com.dental.notice.controller;

import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.notice.dto.NoticeQueryDTO;
import com.dental.notice.dto.NoticeSaveDTO;
import com.dental.notice.service.NoticeService;
import com.dental.notice.vo.NoticeVO;
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

/** 公告公开查询与管理员维护接口。 */
@RestController
@RequestMapping("/api/v1")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/notices")
    public R<PageResult<NoticeVO>> publicList(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return R.ok(noticeService.list(new NoticeQueryDTO(null, null, page, size), true));
    }

    @GetMapping("/admin/notices")
    public R<PageResult<NoticeVO>> adminList(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        requireAdmin();
        return R.ok(noticeService.list(new NoticeQueryDTO(keyword, status, page, size), false));
    }

    @PostMapping("/admin/notices")
    public R<NoticeVO> create(@Valid @RequestBody R<NoticeSaveDTO> request) {
        requireAdmin();
        return R.ok(noticeService.create(request.data()));
    }

    @PutMapping("/admin/notices/{id}")
    public R<NoticeVO> update(@PathVariable long id, @Valid @RequestBody R<NoticeSaveDTO> request) {
        requireAdmin();
        return R.ok(noticeService.update(id, request.data()));
    }

    @DeleteMapping("/admin/notices/{id}")
    public R<Void> delete(@PathVariable long id) {
        requireAdmin();
        noticeService.delete(id);
        return R.ok(null);
    }

    private void requireAdmin() {
        if (!CurrentUser.require().hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅管理员可维护公告");
        }
    }
}
