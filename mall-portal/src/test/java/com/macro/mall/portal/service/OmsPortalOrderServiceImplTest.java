package com.macro.mall.portal.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.domain.OrderParam;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import com.macro.mall.portal.dao.PortalOrderDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OmsPortalOrderServiceImplTest {
    private OmsPortalOrderServiceImpl service;

    @Mock
    private UmsMemberService memberService;
    @Mock
    private OmsOrderMapper orderMapper;
    @Mock
    private OmsOrderItemMapper orderItemMapper;
    @Mock
    private OmsOrderSettingMapper orderSettingMapper;
    @Mock
    private PmsProductMapper productMapper;
    @Mock
    private PmsSkuStockMapper skuStockMapper;
    @Mock
    private RedisService redisService;
    @Mock
    private PortalOrderDao portalOrderDao;

    @BeforeEach
    void setUp() {
        service = new OmsPortalOrderServiceImpl();
        ReflectionTestUtils.setField(service, "memberService", memberService);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(service, "orderSettingMapper", orderSettingMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "skuStockMapper", skuStockMapper);
        ReflectionTestUtils.setField(service, "redisService", redisService);
        ReflectionTestUtils.setField(service, "portalOrderDao", portalOrderDao);
        ReflectionTestUtils.setField(service, "REDIS_DATABASE", "mall");
        ReflectionTestUtils.setField(service, "REDIS_KEY_ORDER_ID", "oms:orderId");
    }

    @Test
    void rejectsOrderDetailOwnedByAnotherMember() {
        UmsMember currentMember = new UmsMember();
        currentMember.setId(7L);
        OmsOrder anotherMembersOrder = new OmsOrder();
        anotherMembersOrder.setId(11L);
        anotherMembersOrder.setMemberId(8L);
        anotherMembersOrder.setDeleteStatus(0);
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(anotherMembersOrder);
        ApiException exception = assertThrows(ApiException.class, () -> service.detail(11L));

        assertEquals("订单不存在", exception.getMessage());
        verify(orderItemMapper, never()).selectByExample(any());
    }

    @Test
    void rejectsPayingAnotherMembersOrder() {
        prepareAnotherMembersOrder();

        ApiException exception = assertThrows(ApiException.class, () -> service.paySuccess(11L, 1));

        assertEquals("订单不存在", exception.getMessage());
        verify(orderMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void rejectsCancellingAnotherMembersOrder() {
        prepareAnotherMembersOrder();

        ApiException exception = assertThrows(ApiException.class, () -> service.cancelUserOrder(11L));

        assertEquals("订单不存在", exception.getMessage());
        verify(orderMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void cancelsExpiredOrderInsteadOfPayingIt() {
        UmsMember currentMember = new UmsMember();
        currentMember.setId(7L);
        OmsOrder expiredOrder = new OmsOrder();
        expiredOrder.setId(11L);
        expiredOrder.setMemberId(7L);
        expiredOrder.setStatus(0);
        expiredOrder.setDeleteStatus(0);
        expiredOrder.setCreateTime(new Date(System.currentTimeMillis() - 31 * 60_000L));
        OmsOrderSetting setting = new OmsOrderSetting();
        setting.setNormalOrderOvertime(30);

        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(portalOrderDao.lockOrder(11L)).thenReturn(expiredOrder);
        when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting);
        when(orderItemMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        assertEquals(-1, service.paySuccess(11L, 1));
        assertEquals(4, expiredOrder.getStatus());
        verify(orderMapper).updateByPrimaryKeySelective(expiredOrder);
    }

    @Test
    void buyNowCartItemUsesSkuPrice() {
        OrderParam orderParam = new OrderParam();
        orderParam.setBuyNowProductId(27L);
        orderParam.setBuyNowSkuId(98L);
        orderParam.setBuyNowQuantity(1);
        PmsProduct product = new PmsProduct();
        product.setId(27L);
        PmsSkuStock skuStock = new PmsSkuStock();
        skuStock.setId(98L);
        skuStock.setProductId(27L);
        skuStock.setPrice(new BigDecimal("199.00"));

        when(productMapper.selectByExample(any())).thenReturn(Collections.singletonList(product));
        when(skuStockMapper.selectByPrimaryKey(98L)).thenReturn(skuStock);

        OmsCartItem item = ReflectionTestUtils.invokeMethod(service, "buildBuyNowCartItem", orderParam);

        assertEquals(new BigDecimal("199.00"), item.getPrice());
        assertEquals(1, item.getQuantity());
    }

    @Test
    void generatesOrderNumberWhenRedisIsUnavailable() {
        OmsOrder order = new OmsOrder();
        order.setSourceType(1);
        order.setPayType(0);
        when(redisService.incr(any(), anyLong())).thenThrow(new IllegalStateException("Redis unavailable"));

        String orderSn = ReflectionTestUtils.invokeMethod(service, "generateOrderSn", order);

        assertTrue(orderSn.matches("^[0-9]{20,}$"));
    }

    @Test
    void paidOrderCanBeConfirmedBeforeShipment() {
        OmsOrder order = prepareOwnOrder(1);

        service.confirmReceiveOrder(11L);

        assertEquals(3, order.getStatus());
        assertEquals(1, order.getConfirmStatus());
        assertNotNull(order.getReceiveTime());
        verify(orderMapper).updateByPrimaryKey(order);
    }

    @Test
    void shippedOrderCanBeConfirmed() {
        OmsOrder order = prepareOwnOrder(2);

        service.confirmReceiveOrder(11L);

        assertEquals(3, order.getStatus());
        verify(orderMapper).updateByPrimaryKey(order);
    }

    @Test
    void unpaidOrderCannotBeConfirmed() {
        prepareOwnOrder(0);

        assertThrows(ApiException.class, () -> service.confirmReceiveOrder(11L));

        verify(orderMapper, never()).updateByPrimaryKey(any());
        verify(orderMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void paidCancellationRestoresPurchasedStockAndBenefitsOnlyOnce() {
        OmsOrder order = prepareOwnOrder(1);
        order.setCouponId(3L);
        order.setUseIntegration(100);
        OmsOrderItem item = new OmsOrderItem();
        item.setProductSkuId(98L);
        item.setProductQuantity(2);
        when(orderItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(item));

        service.cancelUserOrder(11L);

        assertEquals(4, order.getStatus());
        verify(portalOrderDao).restoreSkuStock(Collections.singletonList(item));
        verify(portalOrderDao, never()).releaseSkuStockLock(any());
        verify(portalOrderDao).restoreCoupon(order);
        verify(portalOrderDao).adjustMemberIntegration(7L, 100);
        assertThrows(ApiException.class, () -> service.cancelUserOrder(11L));
        verify(orderMapper).updateByPrimaryKeySelective(order);
    }

    @Test
    void unpaidCancellationReleasesOnlyReservedStock() {
        OmsOrder order = prepareOwnOrder(0);
        OmsOrderItem item = new OmsOrderItem();
        item.setProductSkuId(98L);
        item.setProductQuantity(2);
        when(orderItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(item));

        service.cancelUserOrder(11L);

        assertEquals(4, order.getStatus());
        verify(portalOrderDao).releaseSkuStockLock(Collections.singletonList(item));
        verify(portalOrderDao, never()).restoreSkuStock(any());
    }

    @Test
    void shippedOrderCannotBeCancelled() {
        prepareOwnOrder(2);

        assertThrows(ApiException.class, () -> service.cancelUserOrder(11L));

        verify(orderMapper, never()).updateByPrimaryKeySelective(any());
        verify(portalOrderDao, never()).restoreSkuStock(any());
    }

    @Test
    void delayedCancellationDoesNotCancelPaidOrder() {
        OmsOrder order = new OmsOrder();
        order.setStatus(1);
        order.setDeleteStatus(0);
        when(portalOrderDao.lockOrder(11L)).thenReturn(order);

        service.cancelOrder(11L);

        assertEquals(1, order.getStatus());
        verify(orderMapper, never()).updateByPrimaryKeySelective(any());
        verify(portalOrderDao, never()).restoreSkuStock(any());
    }

    private OmsOrder prepareOwnOrder(int status) {
        UmsMember member = new UmsMember();
        member.setId(7L);
        OmsOrder order = new OmsOrder();
        order.setId(11L);
        order.setMemberId(7L);
        order.setStatus(status);
        order.setDeleteStatus(0);
        when(memberService.getCurrentMember()).thenReturn(member);
        when(portalOrderDao.lockOrder(11L)).thenReturn(order);
        return order;
    }

    private void prepareAnotherMembersOrder() {
        UmsMember currentMember = new UmsMember();
        currentMember.setId(7L);
        OmsOrder anotherMembersOrder = new OmsOrder();
        anotherMembersOrder.setId(11L);
        anotherMembersOrder.setMemberId(8L);
        anotherMembersOrder.setStatus(0);
        anotherMembersOrder.setDeleteStatus(0);
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(portalOrderDao.lockOrder(11L)).thenReturn(anotherMembersOrder);
    }
}
