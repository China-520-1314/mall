package com.macro.mall.portal.service;
import com.macro.mall.portal.controller.MemberDiscussionController;
import com.macro.mall.model.UmsMember;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DiscussionOwnershipTest {
 @Test void cannotDeleteContentWithoutMatchingOwner() {
  var controller=new MemberDiscussionController();var jdbc=mock(JdbcTemplate.class);var members=mock(UmsMemberService.class);
  var member=new UmsMember();member.setId(7L);when(members.getCurrentMember()).thenReturn(member);
  ReflectionTestUtils.setField(controller,"jdbc",jdbc);ReflectionTestUtils.setField(controller,"members",members);
  assertThrows(RuntimeException.class,()->controller.deleteComment(9L));
  verify(jdbc).update(contains("member_id=?"),eq(9L),eq(7L));
  assertThrows(RuntimeException.class,()->controller.deleteReply(10L));
  verify(jdbc).queryForList(contains("o.member_id=?"),eq(10L),eq(7L));
  verify(jdbc,never()).update(startsWith("DELETE"),any(Object[].class));
 }
}
