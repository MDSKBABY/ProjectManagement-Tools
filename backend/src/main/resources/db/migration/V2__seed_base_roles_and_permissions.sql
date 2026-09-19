INSERT INTO app_role (code, name, description, system_role)
VALUES
    ('ADMIN', '系统管理员', '管理用户、角色、项目和审计记录', TRUE),
    ('PROJECT_MANAGER', '项目经理', '创建和管理负责的项目及项目成员', TRUE),
    ('IMPLEMENTER', '实施人员', '查看并更新参与项目的实施信息', TRUE),
    ('TESTER', '测试验证人员', '查看参与项目并执行测试验证工作', TRUE),
    ('VISITOR', '访客', '只读查看被授权的项目', TRUE);

INSERT INTO app_permission (code, name, resource, action, description)
VALUES
    ('user:manage', '管理用户', 'user', 'manage', '创建、停用和维护用户'),
    ('role:manage', '管理角色', 'role', 'manage', '维护角色及其权限'),
    ('project:create', '创建项目', 'project', 'create', '创建新项目'),
    ('project:read', '查看项目', 'project', 'read', '查看被授权的项目'),
    ('project:update', '更新项目', 'project', 'update', '更新被授权的项目'),
    ('project:delete', '删除项目', 'project', 'delete', '软删除被授权的项目'),
    ('project:manage_members', '管理项目成员', 'project', 'manage_members', '维护项目成员及项目内角色'),
    ('audit:read', '查看审计日志', 'audit', 'read', '查看系统审计记录');

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM app_role role
CROSS JOIN app_permission permission
WHERE role.code = 'ADMIN';

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM app_role role
CROSS JOIN app_permission permission
WHERE role.code = 'PROJECT_MANAGER'
  AND permission.code IN (
      'project:create',
      'project:read',
      'project:update',
      'project:delete',
      'project:manage_members'
  );

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM app_role role
CROSS JOIN app_permission permission
WHERE role.code = 'IMPLEMENTER'
  AND permission.code IN ('project:read', 'project:update');

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM app_role role
CROSS JOIN app_permission permission
WHERE role.code IN ('TESTER', 'VISITOR')
  AND permission.code = 'project:read';
