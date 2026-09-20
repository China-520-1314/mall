package com.macro.mall.portal.controller;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@RestController
@RequestMapping("/member/discussion")
public class MemberDiscussionController {
 @Autowired private JdbcTemplate jdbc;
 @Autowired private UmsMemberService members;
 @GetMapping("/list")
 public CommonResult<?> list(@RequestParam(defaultValue="comments") String kind,@RequestParam(defaultValue="1") int pageNum) {
  long member=members.getCurrentMember().getId();int offset=(Math.max(1,Math.min(pageNum,100000))-1)*20;
  String sql=kind.equals("replies") ? "SELECT r.id,r.comment_id AS commentId,r.content,r.create_time AS createTime,c.product_name AS productName,c.product_id AS productId FROM pms_comment_replay r JOIN pms_comment_reply_owner o ON o.reply_id=r.id JOIN pms_comment c ON c.id=r.comment_id WHERE o.member_id=? AND c.show_status=1 ORDER BY r.id DESC LIMIT 20 OFFSET ?" : "SELECT id,product_id AS productId,product_name AS productName,content,create_time AS createTime FROM pms_comment WHERE member_id=? AND show_status=1 ORDER BY id DESC LIMIT 20 OFFSET ?";
  return CommonResult.success(jdbc.queryForList(sql,member,offset));
 }
 @DeleteMapping("/comments/{id}") @Transactional
 public CommonResult<?> deleteComment(@PathVariable long id) {
  int n=jdbc.update("UPDATE pms_comment SET show_status=0 WHERE id=? AND member_id=? AND show_status=1",id,members.getCurrentMember().getId());
  if(n!=1) Asserts.fail("评价不存在或无权删除");return CommonResult.success(n);
 }
 @DeleteMapping("/replies/{id}") @Transactional
 public CommonResult<?> deleteReply(@PathVariable long id) {
  var rows=jdbc.queryForList("SELECT r.comment_id FROM pms_comment_replay r JOIN pms_comment_reply_owner o ON o.reply_id=r.id WHERE r.id=? AND o.member_id=? FOR UPDATE",id,members.getCurrentMember().getId());
  if(rows.isEmpty()) Asserts.fail("回复不存在或无权删除");
  jdbc.update("DELETE FROM pms_comment_replay WHERE id=?",id);jdbc.update("DELETE FROM pms_comment_reply_owner WHERE reply_id=?",id);
  jdbc.update("UPDATE pms_comment SET replay_count=GREATEST(COALESCE(replay_count,0)-1,0) WHERE id=?", rows.get(0).get("comment_id"));
  return CommonResult.success(null);
 }
}
