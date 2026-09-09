package com.macro.mall.portal.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 一个订单的批量评价参数。 */
@Getter
@Setter
public class ProductCommentBatchParam {
    @NotNull
    private Long orderId;

    @Valid
    @NotEmpty
    @Size(max = 20)
    private List<ProductCommentParam> comments;
}
