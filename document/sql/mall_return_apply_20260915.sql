-- 补齐售后进度与寄回物流结构；不修改现有售后记录。
ALTER TABLE oms_order_return_apply
 ADD COLUMN return_delivery_company VARCHAR(100) NULL COMMENT '寄回快递公司',
 ADD COLUMN return_delivery_sn VARCHAR(100) NULL COMMENT '寄回快递单号',
 ADD COLUMN ship_time DATETIME NULL COMMENT '寄回时间';
CREATE TABLE IF NOT EXISTS oms_order_return_apply_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 apply_id BIGINT NOT NULL,
 status INT NOT NULL,
 title VARCHAR(255) NOT NULL,
 note TEXT NULL,
 operator_type INT NOT NULL,
 operator_name VARCHAR(100) NULL,
 create_time DATETIME NOT NULL,
 INDEX idx_apply_time (apply_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
