package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

/** 当前商品最近一笔可评价的已完成购买。 */
@Getter
@Setter
public class ProductCommentPurchase {
    private Long orderId;
    private Long orderItemId;
}
