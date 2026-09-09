-- Apply once to an existing database created from an older mall.sql.
-- If you initialize with the latest mall.sql, the email column and index are already included.
ALTER TABLE `ums_member`
  ADD COLUMN `email` varchar(100) NULL COMMENT '验证邮箱' AFTER `phone`,
  ADD UNIQUE INDEX `idx_email` (`email`);
