package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;
import com.macro.mall.portal.domain.PortalReturnApplyDetail;
import com.macro.mall.portal.domain.ReturnDeliveryParam;

/**
 * 前台订单退货管理Service
 * Created by macro on 2018/10/17.
 */
public interface OmsPortalOrderReturnApplyService {
    /**
     * 提交申请
     */
    int create(OmsOrderReturnApplyParam returnApply);

    /**
     * 分页查询当前会员的售后单
     */
    CommonPage<OmsOrderReturnApply> list(Integer pageNum, Integer pageSize);

    /**
     * 查询售后单详情（含进度时间线）
     */
    PortalReturnApplyDetail detail(Long id);

    /**
     * 会员填写寄回物流（状态：1退货中 -> 4待收货）
     */
    int fillDelivery(ReturnDeliveryParam param);

    /**
     * 会员撤销售后申请（状态：0待处理 -> 5已取消）
     */
    int cancel(Long id);
}
