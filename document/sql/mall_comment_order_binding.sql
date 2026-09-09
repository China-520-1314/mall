-- Apply once to an existing mall database before enabling order-bound comments.
ALTER TABLE `pms_comment`
  ADD COLUMN `member_id` bigint NULL COMMENT '评价会员ID' AFTER `product_id`,
  ADD COLUMN `order_id` bigint NULL COMMENT '来源订单ID' AFTER `member_id`,
  ADD COLUMN `order_item_id` bigint NULL COMMENT '来源订单项ID' AFTER `order_id`,
  ADD UNIQUE INDEX `uk_order_item_id` (`order_item_id`),
  ADD INDEX `idx_member_id` (`member_id`),
  ADD INDEX `idx_order_id` (`order_id`);
