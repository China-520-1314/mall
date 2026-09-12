package com.macro.mall.portal.service;

import com.macro.mall.portal.controller.PmsPortalProductController;
import com.macro.mall.portal.domain.ProductCommentReplyParam;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProductCommentControllerTest {
    @Test
    void publicRepliesRouteStillRequiresLoginForEveryMutation() {
        PmsPortalProductService service = mock(PmsPortalProductService.class);
        PmsPortalProductController controller = new PmsPortalProductController();
        ReflectionTestUtils.setField(controller, "portalProductService", service);
        ProductCommentReplyParam param = new ProductCommentReplyParam();
        param.setContent("参与讨论");

        assertEquals(401L, controller.reply(1L, param, null).getCode());
        assertEquals(401L, controller.like(1L, null).getCode());
        assertEquals(401L, controller.deleteComment(1L, null).getCode());
        assertEquals(401L, controller.deleteReply(1L, null).getCode());
        assertEquals(401L, controller.commentPurchase(26L, null).getCode());
        assertEquals(401L, controller.uncommentedItems(11L, null).getCode());
        assertEquals(401L, controller.myComments(1, 10, null).getCode());
        assertEquals(401L, controller.receivedReplies(1, 10, null).getCode());
        verifyNoInteractions(service);

        assertEquals(200L, controller.replies(1L, 1, 10).getCode());
        verify(service).listCommentReplies(1L, 1, 10);
    }

    @Test
    void memberCommentRoutesPassPaginationAfterLogin() {
        PmsPortalProductService service = mock(PmsPortalProductService.class);
        PmsPortalProductController controller = new PmsPortalProductController();
        ReflectionTestUtils.setField(controller, "portalProductService", service);

        assertEquals(200L, controller.myComments(2, 10, () -> "member").getCode());
        assertEquals(200L, controller.receivedReplies(3, 10, () -> "member").getCode());
        verify(service).listMyComments(2, 10);
        verify(service).listReceivedReplies(3, 10);
    }
}
