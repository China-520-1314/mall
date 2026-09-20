package com.macro.mall.portal.service;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.PortalProductCommentDao;
import com.macro.mall.portal.service.impl.CommentReplyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CommentReplyServiceTest {
 @Test void authenticatedMemberCanReplyAndCountIsIncremented() {
  var service=new CommentReplyService();
  var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);ReflectionTestUtils.setField(service,"jdbc",jdbc);
  var comments=mock(PmsCommentMapper.class); var replies=mock(PmsCommentReplayMapper.class);
  var products=mock(PmsProductMapper.class);var members=mock(UmsMemberService.class);var dao=mock(PortalProductCommentDao.class);
  ReflectionTestUtils.setField(service,"comments",comments);ReflectionTestUtils.setField(service,"replies",replies);
  ReflectionTestUtils.setField(service,"products",products);ReflectionTestUtils.setField(service,"members",members);ReflectionTestUtils.setField(service,"commentDao",dao);
  var c=new PmsComment();c.setShowStatus(1);c.setProductId(56L);when(comments.selectByPrimaryKey(1L)).thenReturn(c);
  var p=new PmsProduct();p.setDeleteStatus(0);p.setPublishStatus(1);when(products.selectByPrimaryKey(56L)).thenReturn(p);
  var m=new UmsMember();m.setUsername("reply-member");m.setId(7L);when(members.getCurrentMember()).thenReturn(m);
  doAnswer(invocation->{((PmsCommentReplay)invocation.getArgument(0)).setId(99L);return 1;}).when(replies).insertSelective(any());
  service.create(1L," hello ");
  verify(jdbc).update(contains("pms_comment_reply_owner"),eq(99L),eq(7L));
  verify(jdbc).update(contains("member_id<>?"),eq("hello"),eq(1L),eq(7L));
  verify(replies).insertSelective(argThat(r->r.getContent().equals("hello") && r.getMemberNickName().equals("reply-member") && r.getType()==0));verify(dao).incrementReplyCount(1L);
  c.setShowStatus(0);assertThrows(RuntimeException.class,()->service.create(1L,"hidden"));
  assertThrows(RuntimeException.class,()->service.create(1L," "));
  verify(replies,times(1)).insertSelective(any());
 }
}
