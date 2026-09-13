package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 会员填写寄回物流参数
 * Created by trae on 2026/09/11.
 */
@Getter
@Setter
public class ReturnDeliveryParam {

    @Schema(title = "售后单id")
    private Long id;

    @Schema(title = "寄回快递公司")
    private String deliveryCompany;

    @Schema(title = "寄回快递单号")
    private String deliverySn;
}
