CREATE TABLE IF NOT EXISTS ums_member_message (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, member_id BIGINT NOT NULL, order_id BIGINT NULL,
 title VARCHAR(255) NOT NULL, content TEXT NOT NULL, read_status INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL, INDEX member_unread(member_id,read_status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS pms_comment_reply_owner (
 reply_id BIGINT PRIMARY KEY, member_id BIGINT NOT NULL, INDEX(member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ums_resource(name,create_time,url,description,category_id)
SELECT '评价与回复管理',NOW(),'/discussionAdmin/**','管理商品评价与回复',1
WHERE NOT EXISTS (SELECT 1 FROM ums_resource WHERE url='/discussionAdmin/**');
INSERT INTO ums_role_resource_relation(role_id,resource_id)
SELECT DISTINCT ar.role_id,r.id FROM ums_admin a
JOIN ums_admin_role_relation ar ON ar.admin_id=a.id
JOIN ums_resource r ON r.url='/discussionAdmin/**'
WHERE a.username='admin' AND NOT EXISTS (
SELECT 1 FROM ums_role_resource_relation rr WHERE rr.role_id=ar.role_id AND rr.resource_id=r.id);
