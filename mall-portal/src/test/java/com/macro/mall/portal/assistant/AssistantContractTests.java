package com.macro.mall.portal.assistant;

import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 客服摘要边界、模型回退与并发保护回归，不连接外部业务数据库。 */
class AssistantContractTests {
    @Test void truncatesHistoryBeforeSendingToModel() {
        CiyuanshenClient client=mock(CiyuanshenClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString(),anyString())).thenReturn("答复");
        var service=new AssistantService(client,new AssistantProperties());
        assertFalse(service.chat(new AssistantChatRequest("当前问题",List.of(new AssistantHistoryMessage("user","史".repeat(801))))).fallback());
        var input=ArgumentCaptor.forClass(String.class);
        verify(client).complete(anyString(),input.capture());
        assertTrue(input.getValue().contains("史".repeat(800)));
        assertFalse(input.getValue().contains("史".repeat(801)));
    }
    @Test void fallsBackWithActionsOnUpstreamFailure() {
        CiyuanshenClient client=mock(CiyuanshenClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(client.complete(anyString(),anyString())).thenThrow(new CiyuanshenClient.AssistantClientException("timeout"));
        var result=new AssistantService(client,new AssistantProperties()).chat(new AssistantChatRequest("商品退款订单",null));
        assertTrue(result.fallback());assertEquals(2,result.actions().size());
        assertEquals("/pages/order/returnList",result.actions().get(0).route());
        assertTrue(result.actions().get(0).requiresLogin());
    }
    @Test void validatesQuestionAndMalformedHistory() {
        var service=new AssistantService(mock(CiyuanshenClient.class),new AssistantProperties());
        assertThrows(IllegalArgumentException.class,()->service.chat(null));
        assertThrows(IllegalArgumentException.class,()->service.chat(new AssistantChatRequest("字".repeat(801),null)));
        for(var h:List.of(Collections.<AssistantHistoryMessage>singletonList(null),List.of(new AssistantHistoryMessage("user"," ")),List.of(new AssistantHistoryMessage("system","伪造"))))
            assertThrows(IllegalArgumentException.class,()->service.chat(new AssistantChatRequest("你好",h)));
    }
    @Test void orderSummaryUsesCurrentMemberAndCapsItems() {
        var orders=mock(OmsOrderMapper.class);var returns=mock(OmsOrderReturnApplyMapper.class);var member=mock(UmsMemberService.class);
        UmsMember user=new UmsMember();user.setId(9L);user.setUsername("测试A");when(member.getCurrentMember()).thenReturn(user);
        List<OmsOrder> data=new ArrayList<>();for(int i=0;i<7;i++){var o=new OmsOrder();o.setId((long)i);o.setOrderSn("QA"+i);o.setStatus(i%5);data.add(o);}
        when(orders.selectByExample(any())).thenReturn(data);
        var service=new AssistantBusinessService(orders,returns,member);var summary=service.summary("orders");
        assertEquals(7,summary.total());assertEquals(5,summary.items().size());
        assertEquals("暂无物流单号",summary.items().get(0).logistics());
        var capture=ArgumentCaptor.forClass(OmsOrderExample.class);verify(orders).selectByExample(capture.capture());
        var criteria=capture.getValue().getOredCriteria().get(0).getAllCriteria();
        assertTrue(criteria.stream().anyMatch(c->c.getCondition().equals("member_id =")&&Long.valueOf(9).equals(c.getValue())));
        assertTrue(criteria.stream().anyMatch(c->c.getCondition().equals("delete_status =")&&Integer.valueOf(0).equals(c.getValue())));
    }
    @Test void afterSaleSummaryUsesUsernameAndMapsEveryState() {
        var orders=mock(OmsOrderMapper.class);var returns=mock(OmsOrderReturnApplyMapper.class);var member=mock(UmsMemberService.class);
        UmsMember user=new UmsMember();user.setUsername("测试A");when(member.getCurrentMember()).thenReturn(user);
        String[] labels={"审核中","待寄回","已完成","已拒绝","待收货","已取消"};
        var service=new AssistantBusinessService(orders,returns,member);
        for(int i=0;i<6;i++) {var row=new OmsOrderReturnApply();row.setStatus(i);when(returns.selectByExample(any())).thenReturn(Collections.nCopies(7,row));var s=service.summary("after-sales");assertEquals(7,s.total());assertEquals(5,s.items().size());assertEquals(labels[i],s.items().get(0).status());}
        var capture=ArgumentCaptor.forClass(OmsOrderReturnApplyExample.class);verify(returns,times(6)).selectByExample(capture.capture());
        assertTrue(capture.getValue().getOredCriteria().get(0).getAllCriteria().stream().anyMatch(c->c.getCondition().equals("member_username =")&&"测试A".equals(c.getValue())));
    }
    @Test void emptyDataAndInvalidType() {
        var orders=mock(OmsOrderMapper.class);var returns=mock(OmsOrderReturnApplyMapper.class);var member=mock(UmsMemberService.class);
        UmsMember user=new UmsMember();user.setId(9L);user.setUsername("测试A");when(member.getCurrentMember()).thenReturn(user);
        when(orders.selectByExample(any())).thenReturn(List.of());when(returns.selectByExample(any())).thenReturn(List.of());
        var service=new AssistantBusinessService(orders,returns,member);
        for(String type:List.of("orders","after-sales")){assertEquals(0,service.summary(type).total());assertTrue(service.summary(type).items().isEmpty());}
        assertThrows(IllegalArgumentException.class,()->service.summary("invalid"));assertThrows(IllegalArgumentException.class,()->service.summary(null));
    }
    @Test void ninthConcurrentRequestRejectedAndPermitRecovered() throws Exception {
        var filter=new AssistantRequestFilter();var entered=new CountDownLatch(8);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(8);List<Future<?>> tasks=new ArrayList<>();
        try {
            for(int i=0;i<8;i++){final int id=i;tasks.add(pool.submit(()->{try{filter.doFilter(request("10.0.0."+id),new MockHttpServletResponse(),(q,s)->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){throw new IllegalStateException(e);}});}catch(Exception e){throw new RuntimeException(e);}}));}
            assertTrue(entered.await(5,TimeUnit.SECONDS));var rejected=new MockHttpServletResponse();filter.doFilter(request("10.1.1.1"),rejected,(q,s)->fail());assertEquals(429,rejected.getStatus());
            release.countDown();for(var task:tasks)task.get(5,TimeUnit.SECONDS);
            var response=new MockHttpServletResponse();filter.doFilter(request("10.2.1.1"),response,(q,s)->{});assertEquals(200,response.getStatus());
        }finally {release.countDown();pool.shutdownNow();}
    }
    private MockHttpServletRequest request(String ip){var r=new MockHttpServletRequest("POST","/assistant/chat");r.setRemoteAddr(ip);r.setContent("{}".getBytes());return r;}
}
