-- 商品评价回复、点赞及删除权限扩展。
-- 已存在的数据库执行一次；重复执行前请先检查列和索引是否存在。
ALTER TABLE `pms_comment_replay`
  ADD COLUMN `member_id` bigint NULL COMMENT '回复作者会员ID' AFTER `comment_id`,
  ADD INDEX `idx_comment_replay_member` (`member_id`),
  ADD INDEX `idx_comment_replay_comment` (`comment_id`);

CREATE TABLE IF NOT EXISTS `pms_comment_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL COMMENT '评价ID',
  `member_id` bigint NOT NULL COMMENT '点赞会员ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_member` (`comment_id`, `member_id`),
  KEY `idx_like_comment` (`comment_id`),
  KEY `idx_like_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价点赞记录';
