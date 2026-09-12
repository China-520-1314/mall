package com.macro.mall.portal.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        service = new OmsPortalOrderServiceImpl();
        ReflectionTestUtils.setField(service, "memberService", memberService);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", orderItemMapper);
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

    private void prepareAnotherMembersOrder() {
        UmsMember currentMember = new UmsMember();
        currentMember.setId(7L);
        OmsOrder anotherMembersOrder = new OmsOrder();
        anotherMembersOrder.setId(11L);
        anotherMembersOrder.setMemberId(8L);
        anotherMembersOrder.setStatus(0);
        anotherMembersOrder.setDeleteStatus(0);
        when(memberService.getCurrentMember()).thenReturn(currentMember);
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(anotherMembersOrder);
    }

    @Test
    void rejectsInvalidPaginationBeforeDatabaseQuery() {
        for (Integer size : new Integer[]{null, 0, -1, 101}) {
            assertThrows(ApiException.class, () -> service.list(-1, 1, size));
        }
        assertThrows(ApiException.class, () -> service.list(5, 1, 10));
        assertThrows(ApiException.class, () -> service.list(-1, 0, 10));
        verify(orderMapper, never()).selectByExample(any());
    }

    @Test
    void returnsEmptyListForMemberWithoutOrders() {
        UmsMember member = new UmsMember();
        member.setId(7L);
        when(memberService.getCurrentMember()).thenReturn(member);
        when(orderMapper.selectByExample(any())).thenReturn(java.util.Collections.emptyList());
        try {
            assertEquals(java.util.Collections.emptyList(), service.list(-1, 1, 5).getList());
        } finally {
            com.github.pagehelper.PageHelper.clearPage();
        }
        verify(orderItemMapper, never()).selectByExample(any());
    }

    @Test
    void returnsOwnOrderDetail() {
        prepareAnotherMembersOrder();
        OmsOrder order = orderMapper.selectByPrimaryKey(11L);
        order.setMemberId(7L);
        when(orderItemMapper.selectByExample(any())).thenReturn(java.util.Collections.emptyList());
        assertEquals(11L, service.detail(11L).getId());
    }

    @Test
    void rejectsDeletedOrderEvenWhenOwned() {
        prepareAnotherMembersOrder();
        OmsOrder order = orderMapper.selectByPrimaryKey(11L);
        order.setMemberId(7L);
        order.setDeleteStatus(1);
        assertThrows(ApiException.class, () -> service.detail(11L));
        verify(orderItemMapper, never()).selectByExample(any());
    }

    @Test
    void rejectsMissingOrder() {
        UmsMember member = new UmsMember();
        member.setId(7L);
        when(memberService.getCurrentMember()).thenReturn(member);
        assertThrows(ApiException.class, () -> service.detail(99L));
        verify(orderItemMapper, never()).selectByExample(any());
    }
}
