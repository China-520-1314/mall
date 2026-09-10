-- 会员站内信表。可独立于主脚本重复执行。
CREATE TABLE IF NOT EXISTS `ums_member_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `order_id` bigint NULL COMMENT '关联订单ID',
  `title` varchar(120) NOT NULL COMMENT '消息标题',
  `content` varchar(1000) NOT NULL COMMENT '消息内容',
  `read_status` tinyint NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member_read_time` (`member_id`, `read_status`, `create_time`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员站内信表';
