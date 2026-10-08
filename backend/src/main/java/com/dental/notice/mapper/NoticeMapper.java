package com.dental.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.notice.entity.NoticeEntity;
import org.apache.ibatis.annotations.Mapper;

/** 公告数据访问。 */
@Mapper
public interface NoticeMapper extends BaseMapper<NoticeEntity> {
}
