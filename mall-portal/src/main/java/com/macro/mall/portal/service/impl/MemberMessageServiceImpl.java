package com.macro.mall.portal.service.impl;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.portal.domain.MemberMessage;
import com.macro.mall.portal.service.MemberMessageService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemberMessageServiceImpl implements MemberMessageService {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public CommonPage<MemberMessage> list(Integer pageNum, Integer pageSize) {
        int safePageNum = pageNum == null ? 1 : Math.max(1, pageNum);
        int safePageSize = pageSize == null ? 10 : Math.min(Math.max(1, pageSize), 50);
        Long memberId = memberService.getCurrentMember().getId();
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ums_member_message WHERE member_id = ?", Long.class, memberId);
        int offset = (safePageNum - 1) * safePageSize;
        List<MemberMessage> messages = jdbcTemplate.query(
                "SELECT id, order_id, title, content, read_status, create_time "
                        + "FROM ums_member_message WHERE member_id = ? "
                        + "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> {
                    MemberMessage message = new MemberMessage();
                    message.setId(rs.getLong("id"));
                    message.setOrderId(rs.getLong("order_id"));
                    message.setTitle(rs.getString("title"));
                    message.setContent(rs.getString("content"));
                    message.setReadStatus(rs.getInt("read_status"));
                    message.setCreateTime(rs.getTimestamp("create_time"));
                    return message;
                }, memberId, safePageSize, offset);
        CommonPage<MemberMessage> result = new CommonPage<>();
        result.setPageNum(safePageNum);
        result.setPageSize(safePageSize);
        result.setTotal(total == null ? 0L : total);
        result.setTotalPage((int) Math.ceil((total == null ? 0D : total) / safePageSize));
        result.setList(messages);
        return result;
    }

    @Override
    public int unreadCount() {
        Long memberId = memberService.getCurrentMember().getId();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ums_member_message WHERE member_id = ? AND read_status = 0",
                Integer.class, memberId);
        return count == null ? 0 : count;
    }

    @Override
    public int markRead(Long id) {
        if (id == null) {
            Asserts.fail("消息不存在");
        }
        Long memberId = memberService.getCurrentMember().getId();
        return jdbcTemplate.update(
                "UPDATE ums_member_message SET read_status = 1 WHERE id = ? AND member_id = ?",
                id, memberId);
    }
}
