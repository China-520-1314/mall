package com.macro.mall.portal.dao;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.OmsOrderDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 前台订单管理自定义Dao
 * Created by macro on 2018/9/4.
 */
public interface PortalOrderDao {
    /** 所有支付、取消、收货操作先锁定同一订单，避免重复扣减和回补。 */
    OmsOrder lockOrder(@Param("orderId") Long orderId);

    int reserveSkuStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /** 已付款取消时恢复真实库存，不修改其他订单的锁定库存。 */
    int restoreSkuStock(@Param("itemList") List<OmsOrderItem> orderItemList);

    int adjustMemberIntegration(@Param("memberId") Long memberId, @Param("delta") Integer delta);

    int useCoupon(@Param("order") OmsOrder order);

    int restoreCoupon(@Param("order") OmsOrder order);

    /**
     * 获取订单及下单商品详情
     */
    OmsOrderDetail getDetail(@Param("orderId") Long orderId);

    /**
     * 修改 pms_sku_stock表的锁定库存及真实库存
     */
    int updateSkuStock(@Param("itemList") List<OmsOrderItem> orderItemList);

    /**
     * 获取超时订单
     * @param minute 超时时间（分）
     */
    List<OmsOrderDetail> getTimeOutOrders(@Param("minute") Integer minute);

    /**
     * 批量修改订单状态
     */
    int updateOrderStatus(@Param("ids") List<Long> ids,@Param("status") Integer status);

    /**
     * 解除取消订单的库存锁定
     */
    int releaseSkuStockLock(@Param("itemList") List<OmsOrderItem> orderItemList);

}
