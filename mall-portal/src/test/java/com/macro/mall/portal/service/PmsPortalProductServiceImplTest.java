package com.macro.mall.portal.service;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsOrderItemMapper;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsCommentMapper;
import com.macro.mall.mapper.PmsBrandMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.PmsComment;
import com.macro.mall.model.PmsCommentReplay;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductCommentDao;
import com.macro.mall.portal.domain.ProductCommentBatchParam;
import com.macro.mall.portal.domain.ProductCommentParam;
import com.macro.mall.portal.domain.ProductCommentReplyParam;
import com.macro.mall.portal.domain.ProductCommentLikeResult;
import com.macro.mall.portal.domain.ProductCommentView;
import com.macro.mall.portal.domain.MemberDetails;
import com.macro.mall.portal.service.impl.PmsPortalProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

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
    @Mock
    private PmsProductMapper productMapper;
    @Mock
    private PmsBrandMapper brandMapper;
    @Mock
    private PmsProductAttributeMapper attributeMapper;
    @Mock
    private PmsSkuStockMapper skuMapper;
    @Mock
    private PortalProductDao productDao;
    @Mock
    private PmsCommentMapper commentMapper;

    @BeforeEach
    void setUp() {
        service = new PmsPortalProductServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(service, "memberService", memberService);
        ReflectionTestUtils.setField(service, "productCommentDao", productCommentDao);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "brandMapper", brandMapper);
        ReflectionTestUtils.setField(service, "productAttributeMapper", attributeMapper);
        ReflectionTestUtils.setField(service, "skuStockMapper", skuMapper);
        ReflectionTestUtils.setField(service, "portalProductDao", productDao);
        ReflectionTestUtils.setField(service, "commentMapper", commentMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        com.github.pagehelper.PageHelper.clearPage();
    }

    @Test
    void detailIncludesRichDescriptionWhileKeepingPublicationFilters() {
        PmsProduct product = new PmsProduct();
        product.setId(47L);
        product.setBrandId(6L);
        product.setProductAttributeCategoryId(16L);
        product.setProductCategoryId(54L);
        product.setPromotionType(0);
        product.setDetailMobileHtml("<p>官网参考起售价，采集日期2026-09-11</p>");
        when(productMapper.selectByExampleWithBLOBs(any())).thenReturn(List.of(product));

        assertEquals(product.getDetailMobileHtml(), service.detail(47L).getProduct().getDetailMobileHtml());
        ArgumentCaptor<PmsProductExample> query = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExampleWithBLOBs(query.capture());
        List<String> conditions = query.getValue().getOredCriteria().get(0).getAllCriteria().stream()
                .map(PmsProductExample.Criterion::getCondition).toList();
        assertEquals(List.of("id =", "delete_status =", "publish_status ="), conditions);
    }

    @Test
    void personalCommentsAndReceivedRepliesAlwaysUseCurrentMember() {
        UmsMember member = new UmsMember();
        member.setId(73L);
        when(memberService.getCurrentMember()).thenReturn(member);
        when(productCommentDao.listMyComments(73L)).thenReturn(List.of());
        when(productCommentDao.listReceivedReplies(73L)).thenReturn(List.of());

        assertTrue(service.listMyComments(1, 10).getList().isEmpty());
        assertTrue(service.listReceivedReplies(1, 10).getList().isEmpty());
        verify(productCommentDao).listMyComments(73L);
        verify(productCommentDao).listReceivedReplies(73L);
    }

    @Test
    void missingOrUnpublishedProductDoesNotExposeRichDescription() {
        assertThrows(ApiException.class, () -> service.detail(47L));
        verify(brandMapper, never()).selectByPrimaryKey(any());
    }

    @Test
    void categoryBrowsingWithoutKeywordReturnsPublishedProducts() {
        PmsProduct product = new PmsProduct();
        product.setId(26L);
        when(productMapper.selectByExample(any())).thenReturn(List.of(product));

        assertEquals(List.of(product), service.search(null, null, 19L, 1, 20, null));

        ArgumentCaptor<PmsProductExample> query = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(query.capture());
        assertEquals(1, query.getValue().getOredCriteria().size());
        List<PmsProductExample.Criterion> filters = query.getValue().getOredCriteria().get(0).getAllCriteria();
        assertEquals(List.of("delete_status =", "publish_status =", "product_category_id ="),
                filters.stream().map(PmsProductExample.Criterion::getCondition).toList());
        assertEquals(19L, filters.get(2).getValue());
    }

    @Test
    void searchMatchesCategoryAndBrandTextWhenNameDoesNotContainKeyword() {
        when(productMapper.selectByExample(any())).thenReturn(Collections.emptyList());

        service.search("手机", null, null, 1, 20, null);

        ArgumentCaptor<PmsProductExample> query = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(query.capture());
        List<List<String>> branchConditions = query.getValue().getOredCriteria().stream()
                .map(criteria -> criteria.getAllCriteria().stream()
                        .map(PmsProductExample.Criterion::getCondition).toList())
                .toList();
        assertEquals(6, branchConditions.size());
        assertEquals(List.of("delete_status =", "publish_status =", "product_category_id =", "name like"), branchConditions.get(0));
        assertEquals(List.of("delete_status =", "publish_status =", "product_category_id =", "brand_name like"), branchConditions.get(4));
        assertEquals(List.of("delete_status =", "publish_status =", "product_category_id =", "product_category_name like"), branchConditions.get(5));
    }

    @Test
    void createsAllCommentsAndMarksOrderCommented() {
        UmsMember member = member(7L);
        OmsOrder order = completedOrder(11L, 7L);
        List<OmsOrderItem> items = List.of(orderItem(101L, 11L, 26L), orderItem(102L, 11L, 27L));
        ProductCommentBatchParam request = batch(11L, comment(101L, 5), comment(102L, 4));

        when(memberService.getCurrentMember()).thenReturn(member);
        when(productCommentDao.lockOrderForComment(11L)).thenReturn(order);
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
        when(productCommentDao.lockOrderForComment(11L)).thenReturn(completedOrder(11L, 8L));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createComments(batch(11L, comment(101L, 5)), "127.0.0.1"));

        assertEquals("订单不存在", exception.getMessage());
        verify(productCommentDao, never()).insert(any(), any(), any(), any());
    }

    @Test
    void rejectsAlreadyCommentedOrderItem() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        when(productCommentDao.lockOrderForComment(11L)).thenReturn(completedOrder(11L, 7L));
        when(orderItemMapper.selectByExample(any())).thenReturn(List.of(orderItem(101L, 11L, 26L)));
        when(productCommentDao.selectCommentedOrderItemIds(List.of(101L))).thenReturn(List.of(101L));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.createComments(batch(11L, comment(101L, 5)), "127.0.0.1"));

        assertEquals("订单中包含已经评价的商品", exception.getMessage());
        verify(productCommentDao, never()).insert(any(), any(), any(), any());
    }

    @Test
    void rejectsEvaluationUntilPurchaseIsCompleted() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        OmsOrder order = completedOrder(11L, 7L);
        order.setStatus(1);
        when(productCommentDao.lockOrderForComment(11L)).thenReturn(order);
        assertEquals("完成订单后才可以评价商品", assertThrows(ApiException.class,
                () -> service.createComments(batch(11L, comment(101L, 5)), "127.0.0.1")).getMessage());
        verify(productCommentDao, never()).insert(any(), any(), any(), any());
    }

    @Test
    void rejectsDeletingAnotherMembersCommentAndReply() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        when(productCommentDao.lockVisibleComment(1L)).thenReturn(visibleComment(1L, 8L));
        PmsCommentReplay reply = new PmsCommentReplay();
        reply.setId(2L);
        reply.setMemberId(8L);
        when(productCommentDao.selectReply(2L)).thenReturn(reply);

        assertThrows(ApiException.class, () -> service.deleteComment(1L));
        assertThrows(ApiException.class, () -> service.deleteCommentReply(2L));
        verify(productCommentDao, never()).hideComment(any(), any());
        verify(productCommentDao, never()).deleteReply(any(), any());
    }

    @Test
    void deletingOwnCommentHidesItWithoutReopeningThePurchase() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        when(productCommentDao.lockVisibleComment(1L)).thenReturn(visibleComment(1L, 7L));
        when(productCommentDao.hideComment(1L, 7L)).thenReturn(1);
        service.deleteComment(1L);
        verify(productCommentDao).hideComment(1L, 7L);
        verify(orderMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void deletedEvaluationCannotBeReadRepliedToOrLiked() {
        ProductCommentReplyParam reply = new ProductCommentReplyParam();
        reply.setContent("讨论内容");
        assertThrows(ApiException.class, () -> service.listCommentReplies(1L, 1, 10));
        assertThrows(ApiException.class, () -> service.createCommentReply(1L, reply));
        assertThrows(ApiException.class, () -> service.toggleCommentLike(1L));
        verify(productCommentDao, never()).listReplies(any());
        verify(productCommentDao, never()).insertReply(any());
        verify(productCommentDao, never()).addLike(any(), any());
    }

    @Test
    void deletingOwnReplyRecomputesCountWhileHoldingParentLock() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        PmsCommentReplay reply = new PmsCommentReplay();
        reply.setId(2L);
        reply.setCommentId(1L);
        reply.setMemberId(7L);
        when(productCommentDao.selectReply(2L)).thenReturn(reply);
        when(productCommentDao.lockVisibleComment(1L)).thenReturn(visibleComment(1L, 8L));
        when(productCommentDao.deleteReply(2L, 7L)).thenReturn(1);
        when(productCommentDao.countReplies(1L)).thenReturn(4);
        service.deleteCommentReply(2L);
        var sequence = inOrder(productCommentDao, commentMapper);
        sequence.verify(productCommentDao).lockVisibleComment(1L);
        sequence.verify(productCommentDao).deleteReply(2L, 7L);
        sequence.verify(productCommentDao).countReplies(1L);
        ArgumentCaptor<PmsComment> updated = ArgumentCaptor.forClass(PmsComment.class);
        sequence.verify(commentMapper).updateByPrimaryKeySelective(updated.capture());
        assertEquals(4, updated.getValue().getReplayCount());
    }

    @Test
    void replyingToAnotherBuyersCommentUsesCurrentAuthorAndUpdatesCount() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        when(productCommentDao.lockVisibleComment(1L)).thenReturn(visibleComment(1L, 8L));
        when(productMapper.selectByExample(any())).thenReturn(List.of(new PmsProduct()));
        when(productCommentDao.countReplies(1L)).thenReturn(2);
        ProductCommentReplyParam param = new ProductCommentReplyParam();
        param.setContent("  续航能用一天吗？  ");
        service.createCommentReply(1L, param);
        ArgumentCaptor<PmsCommentReplay> inserted = ArgumentCaptor.forClass(PmsCommentReplay.class);
        ArgumentCaptor<PmsComment> updated = ArgumentCaptor.forClass(PmsComment.class);
        var sequence = inOrder(productCommentDao, commentMapper);
        sequence.verify(productCommentDao).lockVisibleComment(1L);
        sequence.verify(productCommentDao).insertReply(inserted.capture());
        sequence.verify(productCommentDao).countReplies(1L);
        sequence.verify(commentMapper).updateByPrimaryKeySelective(updated.capture());
        assertEquals(7L, inserted.getValue().getMemberId());
        assertEquals(1L, inserted.getValue().getCommentId());
        assertEquals("续航能用一天吗？", inserted.getValue().getContent());
        assertEquals(2, updated.getValue().getReplayCount());
    }

    @Test
    void blankReplyIsRejectedBeforeInsert() {
        when(productCommentDao.lockVisibleComment(1L)).thenReturn(visibleComment(1L, 8L));
        when(productMapper.selectByExample(any())).thenReturn(List.of(new PmsProduct()));
        ProductCommentReplyParam param = new ProductCommentReplyParam();
        param.setContent("  ");
        assertThrows(ApiException.class, () -> service.createCommentReply(1L, param));
        verify(productCommentDao, never()).insertReply(any());
    }

    @Test
    void likeAndUnlikeUseStoredStateAndUpdateRealCountUnderLock() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        when(productCommentDao.lockVisibleComment(1L)).thenReturn(visibleComment(1L, 8L));
        when(productMapper.selectByExample(any())).thenReturn(List.of(new PmsProduct()));
        when(productCommentDao.hasLiked(1L, 7L)).thenReturn(false, true);
        when(productCommentDao.countLikes(1L)).thenReturn(3, 2);

        ProductCommentLikeResult liked = service.toggleCommentLike(1L);
        ProductCommentLikeResult unliked = service.toggleCommentLike(1L);
        assertTrue(liked.isLiked());
        assertEquals(3, liked.getLikeCount());
        assertFalse(unliked.isLiked());
        assertEquals(2, unliked.getLikeCount());
        var sequence = inOrder(productCommentDao, commentMapper);
        sequence.verify(productCommentDao).lockVisibleComment(1L);
        sequence.verify(productCommentDao).hasLiked(1L, 7L);
        sequence.verify(productCommentDao).addLike(1L, 7L);
        sequence.verify(productCommentDao).countLikes(1L);
        sequence.verify(commentMapper).updateByPrimaryKeySelective(any());
        sequence.verify(productCommentDao).lockVisibleComment(1L);
        sequence.verify(productCommentDao).hasLiked(1L, 7L);
        sequence.verify(productCommentDao).removeLike(1L, 7L);
    }

    @Test
    void commentListReadsCurrentMembersPersistedLikeState() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new MemberDetails(member(7L)), null, Collections.emptyList()));
        when(productMapper.selectByExample(any())).thenReturn(List.of(new PmsProduct()));
        ProductCommentView view = new ProductCommentView();
        view.setLiked(true);
        when(productCommentDao.listComments(26L, 7L)).thenReturn(List.of(view));
        assertTrue(service.listComments(26L, 1, 5).getList().get(0).isLiked());
        verify(productCommentDao).listComments(26L, 7L);
    }

    @Test
    void evaluationFormExcludesAlreadyCommentedPurchaseItems() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(completedOrder(11L, 7L));
        when(orderItemMapper.selectByExample(any())).thenReturn(List.of(orderItem(101L, 11L, 26L), orderItem(102L, 11L, 27L)));
        when(productCommentDao.selectCommentedOrderItemIds(List.of(101L, 102L))).thenReturn(List.of(101L));
        List<OmsOrderItem> result = service.listUncommentedItems(11L);
        assertEquals(List.of(102L), result.stream().map(OmsOrderItem::getId).toList());
    }

    @Test
    void evaluationFormRejectsOtherUsersAndIncompletePurchases() {
        when(memberService.getCurrentMember()).thenReturn(member(7L));
        OmsOrder incomplete = completedOrder(12L, 7L);
        incomplete.setStatus(2);
        when(orderMapper.selectByPrimaryKey(11L)).thenReturn(completedOrder(11L, 8L));
        when(orderMapper.selectByPrimaryKey(12L)).thenReturn(incomplete);
        assertThrows(ApiException.class, () -> service.listUncommentedItems(11L));
        assertThrows(ApiException.class, () -> service.listUncommentedItems(12L));
        verify(orderItemMapper, never()).selectByExample(any());
    }

    private PmsComment visibleComment(Long id, Long memberId) {
        PmsComment comment = new PmsComment();
        comment.setId(id);
        comment.setMemberId(memberId);
        comment.setProductId(26L);
        comment.setShowStatus(1);
        return comment;
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
