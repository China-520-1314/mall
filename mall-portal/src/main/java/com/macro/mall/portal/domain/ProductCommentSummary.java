package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 商品评价聚合统计。 */
@Getter
@Setter
public class ProductCommentSummary {
    private Long totalCount;
    private Long goodCount;
    private Integer goodRate;
    private BigDecimal averageStar;
    private Long star1Count;
    private Long star2Count;
    private Long star3Count;
    private Long star4Count;
    private Long star5Count;
}
