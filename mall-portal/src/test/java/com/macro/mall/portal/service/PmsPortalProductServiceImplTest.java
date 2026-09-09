package com.macro.mall.portal.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.PmsComment;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductCommentDao;
import com.macro.mall.portal.domain.ProductCommentBatchParam;
import com.macro.mall.portal.domain.ProductCommentParam;
import com.macro.mall.portal.service.impl.PmsPortalProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PmsPortalProductServiceImplTest {
    private PmsPortalProductServiceImpl service;

    @Mock
    private OmsOrderMapper orderMapper;
    @Mock
    private OmsOrderItemMapper orderItemMapper;
    @Mock
    private UmsMemberService memberService;
    @Mock
    private PortalProductCommentDao productCommentDao;

    @BeforeEach
    void setUp() {
        service = new PmsPortalProductServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(service, "memberService", memberService);
        ReflectionTestUtils.setField(service, "productCommentDao", productCommentDao);
    }

    @Test
    void createsAllCommentsAndMarksOrderCommented() {
        UmsMember member = member(7L);
        OmsOrder order = completedOrder(11L, 7L);
        List<OmsOrderItem> items = List.of(orderItem(101L, 11L, 26L), orderItem(102L, 11L, 27L));
        ProductCommentBatchParam request = batch(11L, comment(101L, 5), comment(102L, 4));

        when(memberService.getCurrentMember()).thenReturn(member);
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(order);
        when(orderItemMapper.selectByExample(any())).thenReturn(items);
        when(productCommentDao.selectCommentedOrderItemIds(List.of(101L, 102L)))
                .thenReturn(Collections.emptyList());
        when(productCommentDao.countByOrderId(11L)).thenReturn(2);

        service.createComments(request, "127.0.0.1");

        verify(productCommentDao).insert(any(PmsComment.class), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq(101L));
        verify(productCommentDao).insert(any(PmsComment.class), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq(102L));
        ArgumentCaptor<OmsOrder> updateCaptor = ArgumentCaptor.forClass(OmsOrder.class);
        verify(orderMapper).updateByPrimaryKeySelective(updateCaptor.capture());
        assertEquals(11L, updateCaptor.getValue().getId());
        assertNotNull(updateCaptor.getValue().getCommentTime());
    }

    @Test
    void rejectsCommentForAnotherMembersOrder() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(completedOrder(11L, 8L));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createComments(batch(11L, comment(101L, 5)), "127.0.0.1"));

        assertEquals("订单不存在", exception.getMessage());
        verify(productCommentDao, never()).insert(any(), any(), any(), any());
    }

    @Test
    void rejectsAlreadyCommentedOrderItem() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(completedOrder(11L, 7L));
        when(orderItemMapper.selectByExample(any())).thenReturn(List.of(orderItem(101L, 11L, 26L)));
        when(productCommentDao.selectCommentedOrderItemIds(List.of(101L))).thenReturn(List.of(101L));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createComments(batch(11L, comment(101L, 5)), "127.0.0.1"));

        assertEquals("订单中包含已经评价的商品", exception.getMessage());
        verify(productCommentDao, never()).insert(any(), any(), any(), any());
    }

    private UmsMember member(Long id) {
        UmsMember member = new UmsMember();
        member.setId(id);
        member.setUsername("member" + id);
        return member;
    }

    private OmsOrder completedOrder(Long id, Long memberId) {
        OmsOrder order = new OmsOrder();
        order.setId(id);
        order.setMemberId(memberId);
        order.setStatus(3);
        order.setDeleteStatus(0);
        return order;
    }

    private OmsOrderItem orderItem(Long id, Long orderId, Long productId) {
        OmsOrderItem item = new OmsOrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setProductName("商品" + productId);
        return item;
    }

    private ProductCommentParam comment(Long orderItemId, int star) {
        ProductCommentParam comment = new ProductCommentParam();
        comment.setOrderItemId(orderItemId);
        comment.setStar(star);
        comment.setContent("评价内容");
        return comment;
    }

    private ProductCommentBatchParam batch(Long orderId, ProductCommentParam... comments) {
        ProductCommentBatchParam batch = new ProductCommentBatchParam();
        batch.setOrderId(orderId);
        batch.setComments(List.of(comments));
        return batch;
    }
}
