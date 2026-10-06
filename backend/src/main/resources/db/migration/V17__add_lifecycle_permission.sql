INSERT INTO app_permission(code,name,resource,action,description)
VALUES('project_lifecycle:read','查看项目全周期','project_lifecycle','read','查看项目关键节点时间线');
INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM app_role r CROSS JOIN app_permission p
WHERE r.code IN('ADMIN','PROJECT_MANAGER','IMPLEMENTER','TESTER','VISITOR')
AND p.code='project_lifecycle:read';
