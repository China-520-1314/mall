package com.macro.mall.portal.dao;

import com.macro.mall.model.PmsComment;
import com.macro.mall.portal.domain.ProductCommentSummary;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 前台商品评价自定义数据访问接口。 */
public interface PortalProductCommentDao {
    List<Long> selectCommentedOrderItemIds(@Param("orderItemIds") List<Long> orderItemIds);

    int countByOrderId(@Param("orderId") Long orderId);

    int insert(@Param("comment") PmsComment comment,
               @Param("memberId") Long memberId,
               @Param("orderId") Long orderId,
               @Param("orderItemId") Long orderItemId);

    ProductCommentSummary getSummary(@Param("productId") Long productId);
}
