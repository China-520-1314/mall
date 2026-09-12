package com.macro.mall.portal.domain;

import com.macro.mall.model.PmsComment;
import lombok.Getter;
import lombok.Setter;

/** 公开评价内容与当前用户的互动状态，不包含订单或网络信息。 */
@Getter
@Setter
public class ProductCommentView extends PmsComment {
    private boolean liked;
}
