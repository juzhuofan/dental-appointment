package com.dental.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.dental.notice.dto.NoticeQueryDTO;
import com.dental.notice.dto.NoticeSaveDTO;
import com.dental.notice.entity.NoticeEntity;
import com.dental.notice.mapper.NoticeMapper;
import com.dental.notice.vo.NoticeVO;
import com.dental.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 公告发布及查询。 */
@Service
public class NoticeService {

    private final NoticeMapper noticeMapper;
    private final OperationLogService operationLogService;

    public NoticeService(NoticeMapper noticeMapper, OperationLogService operationLogService) {
        this.noticeMapper = noticeMapper;
        this.operationLogService = operationLogService;
    }

    public PageResult<NoticeVO> list(NoticeQueryDTO query, boolean publicOnly) {
        checkPage(query.page(), query.size());
        LambdaQueryWrapper<NoticeEntity> wrapper = new LambdaQueryWrapper<>();
        if (publicOnly) {
            LocalDateTime now = TimeUtil.nowUtc();
            wrapper.eq(NoticeEntity::getStatus, 1)
                    .and(condition -> condition.isNull(NoticeEntity::getPublishAt)
                            .or().le(NoticeEntity::getPublishAt, now))
                    .and(condition -> condition.isNull(NoticeEntity::getExpireAt)
                            .or().gt(NoticeEntity::getExpireAt, now));
        } else if (query.status() != null) {
            wrapper.eq(NoticeEntity::getStatus, query.status());
        }
        if (hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(condition -> condition.like(NoticeEntity::getTitle, keyword)
                    .or().like(NoticeEntity::getContent, keyword));
        }
        wrapper.orderByDesc(NoticeEntity::getPublishAt).orderByDesc(NoticeEntity::getId);
        Page<NoticeEntity> result = noticeMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        List<NoticeVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), query.page(), query.size());
    }

    @Transactional
    public NoticeVO create(NoticeSaveDTO input) {
        NoticeEntity notice = new NoticeEntity();
        copy(input, notice);
        notice.setCreatedBy(CurrentUser.require().id());
        notice.setDeleted(0);
        notice.setCreatedAt(TimeUtil.nowUtc());
        notice.setUpdatedAt(notice.getCreatedAt());
        noticeMapper.insert(notice);
        operationLogService.record("CREATE", "NOTICE", notice.getId().toString(), "新增诊所公告");
        return toVO(notice);
    }

    @Transactional
    public NoticeVO update(long id, NoticeSaveDTO input) {
        NoticeEntity notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw BusinessException.missing("公告不存在");
        }
        copy(input, notice);
        notice.setUpdatedAt(TimeUtil.nowUtc());
        if (noticeMapper.updateById(notice) != 1) {
            throw BusinessException.conflict("公告状态已变化，请刷新后重试");
        }
        operationLogService.record("UPDATE", "NOTICE", Long.toString(id), "更新诊所公告");
        return toVO(notice);
    }

    @Transactional
    public void delete(long id) {
        if (noticeMapper.selectById(id) == null) {
            throw BusinessException.missing("公告不存在");
        }
        LambdaUpdateWrapper<NoticeEntity> wrapper = new LambdaUpdateWrapper<NoticeEntity>()
                .eq(NoticeEntity::getId, id)
                .set(NoticeEntity::getStatus, 2)
                .set(NoticeEntity::getDeleted, 1)
                .set(NoticeEntity::getUpdatedAt, TimeUtil.nowUtc());
        if (noticeMapper.update(null, wrapper) != 1) {
            throw BusinessException.conflict("公告状态已变化，请刷新后重试");
        }
        operationLogService.record("DELETE", "NOTICE", Long.toString(id), "逻辑删除诊所公告");
    }

    private void copy(NoticeSaveDTO input, NoticeEntity notice) {
        LocalDateTime publishAt = TimeUtil.toUtc(input.publishAt());
        if (input.status() == 1 && publishAt == null) {
            publishAt = TimeUtil.nowUtc();
        }
        LocalDateTime expireAt = TimeUtil.toUtc(input.expireAt());
        if (expireAt != null && publishAt != null && !expireAt.isAfter(publishAt)) {
            throw BusinessException.bad("公告失效时间必须晚于发布时间");
        }
        notice.setTitle(input.title().trim());
        notice.setContent(input.content().trim());
        notice.setStatus(input.status());
        notice.setPublishAt(publishAt);
        notice.setExpireAt(expireAt);
    }

    private NoticeVO toVO(NoticeEntity notice) {
        return new NoticeVO(notice.getId(), notice.getTitle(), notice.getContent(), notice.getStatus(),
                TimeUtil.toClinic(notice.getPublishAt()), TimeUtil.toClinic(notice.getExpireAt()),
                TimeUtil.toClinic(notice.getCreatedAt()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void checkPage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.bad("分页参数无效");
        }
    }
}
