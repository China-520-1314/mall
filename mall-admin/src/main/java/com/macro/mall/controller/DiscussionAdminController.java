package com.macro.mall.controller;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.Asserts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
@RestController
@RequestMapping("/discussionAdmin")
public class DiscussionAdminController {
 @Autowired private JdbcTemplate jdbc;
 private void check(Principal principal) {
  if(principal==null || !"admin".equals(principal.getName())) Asserts.fail("仅系统管理员可以管理评价");
 }
 @GetMapping("/list") public CommonResult<?> list(Principal principal,@RequestParam(defaultValue="comments") String kind,@RequestParam(defaultValue="1") int pageNum) {
  check(principal); int offset=(Math.max(1,Math.min(pageNum,100000))-1)*20;
  return CommonResult.success(jdbc.queryForList(kind.equals("replies") ? "SELECT r.id,r.content,r.member_nick_name AS author,c.product_name AS productName FROM pms_comment_replay r JOIN pms_comment c ON c.id=r.comment_id WHERE c.show_status=1 ORDER BY r.id DESC LIMIT 20 OFFSET ?" : "SELECT id,content,member_nick_name AS author,product_name AS productName FROM pms_comment WHERE show_status=1 ORDER BY id DESC LIMIT 20 OFFSET ?",offset));
 }
 @DeleteMapping("/{kind}/{id}") @Transactional public CommonResult<?> delete(Principal principal,@PathVariable String kind,@PathVariable long id) {
  check(principal);
  if(kind.equals("comments")) jdbc.update("UPDATE pms_comment SET show_status=0 WHERE id=?",id);
  else if(kind.equals("replies")) {
   var rows=jdbc.queryForList("SELECT comment_id FROM pms_comment_replay WHERE id=? FOR UPDATE",id);
   if(!rows.isEmpty()) {
    jdbc.update("DELETE FROM pms_comment_replay WHERE id=?",id);jdbc.update("DELETE FROM pms_comment_reply_owner WHERE reply_id=?",id);
    jdbc.update("UPDATE pms_comment SET replay_count=GREATEST(COALESCE(replay_count,0)-1,0) WHERE id=?",rows.get(0).get("comment_id"));
   }
  } else Asserts.fail("类型无效");
  return CommonResult.success(null);
 }
}
