package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.api.CommonPage;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;

@Service
public class CommentReplyService {
    @Autowired private PmsCommentMapper comments;
    @Autowired private PmsCommentReplayMapper replies;
    @Autowired private PmsProductMapper products;
    @Autowired private UmsMemberService members;
    @Autowired private com.macro.mall.portal.dao.PortalProductCommentDao commentDao;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private void check(Long id) {
        PmsComment c=comments.selectByPrimaryKey(id);
        if(c==null || !Integer.valueOf(1).equals(c.getShowStatus())) Asserts.fail("评价不存在或已隐藏");
        PmsProduct p=products.selectByPrimaryKey(c.getProductId());
        if(p==null || !Integer.valueOf(0).equals(p.getDeleteStatus()) || !Integer.valueOf(1).equals(p.getPublishStatus())) Asserts.fail("商品不存在或已下架");
    }
    public CommonPage<PmsCommentReplay> list(Long id,int pageNum,int pageSize) {
        check(id);
        PmsCommentReplayExample e=new PmsCommentReplayExample();
        e.createCriteria().andCommentIdEqualTo(id); e.setOrderByClause("create_time desc, id desc");
        PageHelper.startPage(pageNum,pageSize);
        return CommonPage.restPage(replies.selectByExample(e));
    }
    @Transactional
    public void create(Long id,String content) {
        if(content==null || content.isBlank() || content.length()>1000) Asserts.fail("请输入1到1000字的回复");
        UmsMember m=members.getCurrentMember(); check(id);
        PmsCommentReplay r=new PmsCommentReplay(); r.setCommentId(id);
        r.setContent(content.trim()); r.setCreateTime(new Date()); r.setType(0);
        r.setMemberNickName(m.getNickname()==null?m.getUsername():m.getNickname()); r.setMemberIcon(m.getIcon());
        replies.insertSelective(r); commentDao.incrementReplyCount(id);
        jdbc.update("INSERT INTO pms_comment_reply_owner(reply_id,member_id) VALUES (?,?)",r.getId(),m.getId());
        jdbc.update("INSERT INTO ums_member_message(member_id,title,content,read_status,create_time) SELECT member_id,'收到评价回复',CONCAT('商品《',product_name,'》的评价收到回复：',?),0,NOW() FROM pms_comment WHERE id=? AND member_id IS NOT NULL AND member_id<>?", content.trim(),id,m.getId());
    }
}
