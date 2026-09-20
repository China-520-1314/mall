INSERT INTO ums_resource(name,create_time,url,description,category_id)
SELECT '经营数据大屏',NOW(),'/operationsDashboard/**','查看商城经营统计',1
WHERE NOT EXISTS(SELECT 1 FROM ums_resource WHERE url='/operationsDashboard/**');
INSERT INTO ums_role_resource_relation(role_id,resource_id)
SELECT DISTINCT ar.role_id,r.id FROM ums_admin a JOIN ums_admin_role_relation ar ON ar.admin_id=a.id
JOIN ums_resource r ON r.url='/operationsDashboard/**'
WHERE a.username='admin' AND NOT EXISTS(SELECT 1 FROM ums_role_resource_relation rr WHERE rr.role_id=ar.role_id AND rr.resource_id=r.id);
