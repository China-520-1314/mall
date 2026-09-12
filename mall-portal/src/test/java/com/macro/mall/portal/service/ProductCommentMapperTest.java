package com.macro.mall.portal.service;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 无全局下划线转驼峰配置时，评价与回复仍须正确映射作者、内容和计数字段。 */
class ProductCommentMapperTest {
    @Test
    void commentsAndRepliesMapAuthorIdsAndInteractionDataExplicitly() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : new String[] {
                "com/macro/mall/mapper/PmsCommentMapper.xml",
                "com/macro/mall/mapper/PmsCommentReplayMapper.xml",
                "com/macro/mall/mapper/OmsOrderMapper.xml",
                "dao/PortalProductCommentDao.xml"}) {
            try (InputStream stream = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        String namespace = "com.macro.mall.portal.dao.PortalProductCommentDao.";
        Map<String, String> commentFields = configuration.getMappedStatement(namespace + "listComments")
                .getResultMaps().get(0).getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getProperty(), mapping -> mapping.getColumn()));
        assertEquals("member_id", commentFields.get("memberId"));
        assertEquals("product_id", commentFields.get("productId"));
        assertEquals("content", commentFields.get("content"));
        assertEquals("liked", commentFields.get("liked"));
        assertEquals("collect_couont", commentFields.get("collectCouont"));
        assertEquals("replay_count", commentFields.get("replayCount"));
        assertTrue(configuration.getMappedStatement(namespace + "listReplies").getResultMaps().get(0)
                .getResultMappings().stream().anyMatch(mapping -> "memberId".equals(mapping.getProperty())
                        && "member_id".equals(mapping.getColumn())));
        assertTrue(configuration.getMappedStatement(namespace + "lockOrderForComment")
                .getResultMaps().get(0).getResultMappings().stream()
                .anyMatch(mapping -> "memberId".equals(mapping.getProperty())));

        Map<String, String> myFields = configuration.getMappedStatement(namespace + "listMyComments")
                .getResultMaps().get(0).getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getProperty(), mapping -> mapping.getColumn()));
        assertEquals("product_pic", myFields.get("productPic"));
        assertEquals("replay_count", myFields.get("replayCount"));
        assertEquals("create_time", myFields.get("createTime"));
        Map<String, String> receivedFields = configuration.getMappedStatement(namespace + "listReceivedReplies")
                .getResultMaps().get(0).getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getProperty(), mapping -> mapping.getColumn()));
        assertEquals("reply_id", receivedFields.get("replyId"));
        assertEquals("comment_id", receivedFields.get("commentId"));
        assertEquals("reply_member_nick_name", receivedFields.get("replyMemberNickName"));
        assertEquals("reply_content", receivedFields.get("replyContent"));
        assertEquals("reply_create_time", receivedFields.get("replyCreateTime"));
    }
}
