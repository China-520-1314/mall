package com.macro.mall.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.dto.UmsMemberMessageParam;
import com.macro.mall.service.UmsMemberMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 将管理端消息持久化到会员站内信表。 */
@Service
public class UmsMemberMessageServiceImpl implements UmsMemberMessageService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public int sendToOrderMember(Long orderId, UmsMemberMessageParam param) {
        if (orderId == null || param == null) {
            Asserts.fail("订单或消息内容不能为空");
        }
        Long memberId = jdbcTemplate.query(
                "SELECT member_id FROM oms_order WHERE id = ? AND delete_status = 0",
                ps -> ps.setLong(1, orderId),
                rs -> rs.next() ? rs.getLong("member_id") : null);
        if (memberId == null) {
            Asserts.fail("订单不存在");
        }
        return jdbcTemplate.update(
                "INSERT INTO ums_member_message(member_id, order_id, title, content, read_status, create_time) VALUES (?, ?, ?, ?, 0, NOW())",
                memberId, orderId, param.getTitle().trim(), param.getContent().trim());
    }
}
