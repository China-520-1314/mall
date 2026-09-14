package com.macro.mall.portal.assistant;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderExample;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("!assistant")
public class AssistantBusinessService {
    private final OmsOrderMapper orderMapper;
    private final OmsOrderReturnApplyMapper returnApplyMapper;
    private final UmsMemberService memberService;

    public AssistantBusinessService(OmsOrderMapper orderMapper, OmsOrderReturnApplyMapper returnApplyMapper,
                                    UmsMemberService memberService) {
        this.orderMapper = orderMapper;
        this.returnApplyMapper = returnApplyMapper;
        this.memberService = memberService;
    }

    public AssistantBusinessSummary summary(String type) {
        if ("orders".equals(type)) return orders();
        if ("after-sales".equals(type)) return afterSales();
        throw new IllegalArgumentException("不支持的客服业务类型");
    }

    private AssistantBusinessSummary orders() {
        Long memberId = memberService.getCurrentMember().getId();
        OmsOrderExample example = new OmsOrderExample();
        example.createCriteria().andMemberIdEqualTo(memberId).andDeleteStatusEqualTo(0);
        example.setOrderByClause("create_time desc");
        List<OmsOrder> orders = orderMapper.selectByExample(example);
        List<AssistantBusinessSummary.Item> items = orders.stream().limit(5).map(order ->
                new AssistantBusinessSummary.Item(order.getId(), order.getOrderSn(), orderStatus(order.getStatus()),
                        format(order.getCreateTime()), logistics(order))).collect(Collectors.toList());
        return new AssistantBusinessSummary("orders", orders.size(), items);
    }

    private AssistantBusinessSummary afterSales() {
        String username = memberService.getCurrentMember().getUsername();
        OmsOrderReturnApplyExample example = new OmsOrderReturnApplyExample();
        example.createCriteria().andMemberUsernameEqualTo(username);
        example.setOrderByClause("create_time desc");
        List<OmsOrderReturnApply> applies = returnApplyMapper.selectByExample(example);
        List<AssistantBusinessSummary.Item> items = applies.stream().limit(5).map(apply ->
                new AssistantBusinessSummary.Item(apply.getId(), apply.getOrderSn(), afterSaleStatus(apply.getStatus()),
                        format(apply.getCreateTime()), "")).collect(Collectors.toList());
        return new AssistantBusinessSummary("after-sales", applies.size(), items);
    }

    private String orderStatus(Integer status) {
        return switch (status == null ? -1 : status) {
            case 0 -> "待付款";
            case 1 -> "待发货";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已关闭";
            default -> "未知状态";
        };
    }

    private String afterSaleStatus(Integer status) {
        return switch (status == null ? -1 : status) {
            case 0 -> "审核中";
            case 1 -> "待寄回";
            case 2 -> "已完成";
            case 3 -> "已拒绝";
            case 4 -> "待收货";
            case 5 -> "已取消";
            default -> "处理中";
        };
    }

    private String format(Date date) {
        return date == null ? "" : date.toInstant().toString();
    }

    private String logistics(OmsOrder order) {
        if (order.getDeliverySn() == null || order.getDeliverySn().isBlank()) return "暂无物流单号";
        return (order.getDeliveryCompany() == null ? "" : order.getDeliveryCompany() + " ") + order.getDeliverySn();
    }
}
