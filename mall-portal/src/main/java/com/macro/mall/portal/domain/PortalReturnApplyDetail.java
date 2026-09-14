package com.macro.mall.portal.domain;

import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.model.OmsOrderReturnApplyLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 前台售后单详情（含进度时间线、商家退货收货地址）
 * Created by trae on 2026/09/11.
 */
@Getter
@Setter
public class PortalReturnApplyDetail extends OmsOrderReturnApply {

    @Schema(title = "进度日志列表（时间线）")
    private List<OmsOrderReturnApplyLog> logList;

    @Schema(title = "商家收货点名称")
    private String companyAddressName;

    @Schema(title = "商家收货人")
    private String companyReceiverName;

    @Schema(title = "商家收货电话")
    private String companyReceiverPhone;

    @Schema(title = "商家收货详细地址")
    private String companyDetailAddress;
}
