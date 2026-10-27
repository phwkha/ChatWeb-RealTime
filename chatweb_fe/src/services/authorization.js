export const PERMISSIONS = Object.freeze({
  // User Administration
  ADMIN_VIEW_USERS: "ADMIN_VIEW_USERS",
  ADMIN_VIEW_ONLINE_USERS: "ADMIN_VIEW_ONLINE_USERS",
  ADMIN_VIEW_USER_DETAIL: "ADMIN_VIEW_USER_DETAIL",
  ADMIN_VIEW_USER_ADDRESSES: "ADMIN_VIEW_USER_ADDRESSES",
  ADMIN_CREATE: "ADMIN_CREATE",
  ADMIN_UPDATE_USER: "ADMIN_UPDATE_USER",
  ADMIN_UPDATE_USER_ADDRESS: "ADMIN_UPDATE_USER_ADDRESS",
  ADMIN_DELETE_USER: "ADMIN_DELETE_USER",
  ADMIN_DELETE_USER_ADDRESS: "ADMIN_DELETE_USER_ADDRESS",
  ADMIN_LOCK: "ADMIN_LOCK",
  ADMIN_UNLOCK: "ADMIN_UNLOCK",
  ADMIN_DELETE_AVATAR: "ADMIN_DELETE_AVATAR",

  // Role & Permission Management
  ADMIN_ROLE_VIEW_ALL: "ADMIN_ROLE_VIEW_ALL",
  ADMIN_ROLE_VIEW_ALL_PERMISSION: "ADMIN_ROLE_VIEW_ALL_PERMISSION",
  ADMIN_ROLE_ADD: "ADMIN_ROLE_ADD",
  ADMIN_ROLE_UPDATE: "ADMIN_ROLE_UPDATE",
  ADMIN_ROLE_DELETE: "ADMIN_ROLE_DELETE",

  // Backward-compatibility aliases for Role & Permission Management
  ROLE_VIEW_ALL: "ADMIN_ROLE_VIEW_ALL",
  ROLE_VIEW_ALL_PERMISSION: "ADMIN_ROLE_VIEW_ALL_PERMISSION",
  ROLE_ADD: "ADMIN_ROLE_ADD",
  ROLE_UPDATE: "ADMIN_ROLE_UPDATE",
  ROLE_DELETE: "ADMIN_ROLE_DELETE",

  // System Communication
  ADMIN_SEND_EMAIL: "ADMIN_SEND_EMAIL",
  SEND_EMAIL: "ADMIN_SEND_EMAIL",
  ADMIN_SEND_MESSAGE: "ADMIN_SEND-MESSAGE",

  // Report Moderation
  ADMIN_VIEW_REPORTS: "ADMIN_VIEW_REPORTS",
  ADMIN_RESOLVE_REPORTS: "ADMIN_RESOLVE_REPORTS",
  ADMIN_DELETE_REPORTS: "ADMIN_DELETE_REPORTS",

  // User Permissions
  USER_SEND_WORLD_CHAT: "USER_SEND_WORLD_CHAT",
});

export const USER_MANAGEMENT_PERMISSIONS = Object.freeze([
  PERMISSIONS.ADMIN_VIEW_USERS,
  PERMISSIONS.ADMIN_VIEW_ONLINE_USERS,
  PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
  PERMISSIONS.ADMIN_VIEW_USER_ADDRESSES,
  PERMISSIONS.ADMIN_CREATE,
  PERMISSIONS.ADMIN_UPDATE_USER,
  PERMISSIONS.ADMIN_UPDATE_USER_ADDRESS,
  PERMISSIONS.ADMIN_DELETE_USER,
  PERMISSIONS.ADMIN_DELETE_USER_ADDRESS,
  PERMISSIONS.ADMIN_LOCK,
  PERMISSIONS.ADMIN_UNLOCK,
  PERMISSIONS.ADMIN_DELETE_AVATAR,
]);

export const USER_DRAWER_ACTION_PERMISSIONS = Object.freeze([
  PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
  PERMISSIONS.ADMIN_VIEW_USER_ADDRESSES,
  PERMISSIONS.ADMIN_UPDATE_USER,
  PERMISSIONS.ADMIN_UPDATE_USER_ADDRESS,
  PERMISSIONS.ADMIN_DELETE_USER,
  PERMISSIONS.ADMIN_DELETE_USER_ADDRESS,
  PERMISSIONS.ADMIN_LOCK,
  PERMISSIONS.ADMIN_UNLOCK,
  PERMISSIONS.ADMIN_DELETE_AVATAR,
]);

export const REPORT_MODERATION_PERMISSIONS = Object.freeze([
  PERMISSIONS.ADMIN_VIEW_REPORTS,
  PERMISSIONS.ADMIN_RESOLVE_REPORTS,
  PERMISSIONS.ADMIN_DELETE_REPORTS,
]);

export const ROLE_MANAGEMENT_PERMISSIONS = Object.freeze([
  PERMISSIONS.ADMIN_ROLE_VIEW_ALL,
  PERMISSIONS.ADMIN_ROLE_VIEW_ALL_PERMISSION,
  PERMISSIONS.ADMIN_ROLE_ADD,
  PERMISSIONS.ADMIN_ROLE_UPDATE,
  PERMISSIONS.ADMIN_ROLE_DELETE,
]);

export const PERMISSION_PREREQUISITES = Object.freeze({
  [PERMISSIONS.ADMIN_VIEW_USER_DETAIL]: [PERMISSIONS.ADMIN_VIEW_USERS],
  [PERMISSIONS.ADMIN_UPDATE_USER]: [
    PERMISSIONS.ADMIN_VIEW_USERS,
    PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
  ],
  [PERMISSIONS.ADMIN_LOCK]: [
    PERMISSIONS.ADMIN_VIEW_USERS,
    PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
  ],
  [PERMISSIONS.ADMIN_UNLOCK]: [
    PERMISSIONS.ADMIN_VIEW_USERS,
    PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
  ],
  [PERMISSIONS.ADMIN_DELETE_USER]: [
    PERMISSIONS.ADMIN_VIEW_USERS,
    PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
  ],
  [PERMISSIONS.ADMIN_DELETE_AVATAR]: [
    PERMISSIONS.ADMIN_VIEW_USERS,
    PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
  ],
  [PERMISSIONS.ADMIN_VIEW_USER_ADDRESSES]: [
    PERMISSIONS.ADMIN_VIEW_USERS,
    PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
  ],
  [PERMISSIONS.ADMIN_UPDATE_USER_ADDRESS]: [
    PERMISSIONS.ADMIN_VIEW_USERS,
    PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
    PERMISSIONS.ADMIN_VIEW_USER_ADDRESSES,
  ],
  [PERMISSIONS.ADMIN_DELETE_USER_ADDRESS]: [
    PERMISSIONS.ADMIN_VIEW_USERS,
    PERMISSIONS.ADMIN_VIEW_USER_DETAIL,
    PERMISSIONS.ADMIN_VIEW_USER_ADDRESSES,
  ],
  [PERMISSIONS.ADMIN_ROLE_ADD]: [
    PERMISSIONS.ADMIN_ROLE_VIEW_ALL,
    PERMISSIONS.ADMIN_ROLE_VIEW_ALL_PERMISSION,
  ],
  [PERMISSIONS.ADMIN_ROLE_UPDATE]: [
    PERMISSIONS.ADMIN_ROLE_VIEW_ALL,
    PERMISSIONS.ADMIN_ROLE_VIEW_ALL_PERMISSION,
  ],
  [PERMISSIONS.ADMIN_ROLE_DELETE]: [PERMISSIONS.ADMIN_ROLE_VIEW_ALL],
  [PERMISSIONS.ADMIN_RESOLVE_REPORTS]: [PERMISSIONS.ADMIN_VIEW_REPORTS],
  [PERMISSIONS.ADMIN_DELETE_REPORTS]: [PERMISSIONS.ADMIN_VIEW_REPORTS],
});

export function resolvePermissionIdsWithPrerequisites(
  currentPermissionIds = [],
  addedPermissionId,
  availablePermissions = [],
) {
  const nextIds = new Set(
    Array.isArray(currentPermissionIds) ? currentPermissionIds : [],
  );
  if (addedPermissionId !== undefined && addedPermissionId !== null) {
    nextIds.add(addedPermissionId);
  }

  const addedPerm = (availablePermissions || []).find(
    (p) =>
      p &&
      (p.id === addedPermissionId ||
        String(p.id) === String(addedPermissionId)),
  );
  if (!addedPerm || !addedPerm.name) {
    return Array.from(nextIds);
  }

  const queue = [addedPerm.name];
  const visited = new Set([addedPerm.name]);

  while (queue.length > 0) {
    const currentName = queue.shift();
    const canonCurrent = PERMISSIONS[currentName] || currentName;
    const requiredNames = PERMISSION_PREREQUISITES[canonCurrent] || [];

    for (const reqName of requiredNames) {
      const canonReq = PERMISSIONS[reqName] || reqName;
      const match = (availablePermissions || []).find(
        (p) =>
          p &&
          (p.name === reqName || (PERMISSIONS[p.name] || p.name) === canonReq),
      );
      if (match && match.id !== undefined && match.id !== null) {
        nextIds.add(match.id);
      }
      if (!visited.has(canonReq)) {
        visited.add(canonReq);
        queue.push(canonReq);
      }
    }
  }

  return Array.from(nextIds);
}

export const SYSTEM_ROLES = Object.freeze({
  ADMIN: "ADMIN",
  USER: "USER",
});

export const ROOT_ADMIN_USERNAME = "admin";

export function isRootAdmin(userOrUsername) {
  if (!userOrUsername) return false;
  const username =
    typeof userOrUsername === "string"
      ? userOrUsername
      : userOrUsername.username;
  return (
    typeof username === "string" &&
    username.trim().toLowerCase() === ROOT_ADMIN_USERNAME
  );
}

function hasExplicitPermissions(user) {
  if (!user || user.permissions === undefined || user.permissions === null)
    return false;
  if (user.permissions instanceof Set) return user.permissions.size > 0;
  return Array.isArray(user.permissions) && user.permissions.length > 0;
}

export const ADMIN_CONSOLE_PERMISSIONS = Object.freeze(
  Array.from(
    new Set(
      Object.values(PERMISSIONS).filter(
        (p) => typeof p === "string" && p.startsWith("ADMIN_"),
      ),
    ),
  ),
);

const ADMIN_PERMISSION_PATTERN = /^ADMIN_/;

export function normalizeRoleName(role) {
  if (!role) return "";
  if (typeof role === "object" && role.name)
    return String(role.name).trim().toUpperCase();
  return String(role).trim().toUpperCase();
}

export function isSystemRole(roleOrName) {
  const normalized = normalizeRoleName(roleOrName).replace(/^ROLE_/, "");
  return normalized === SYSTEM_ROLES.ADMIN || normalized === SYSTEM_ROLES.USER;
}

export function isSuperAdmin(user) {
  if (!user) return false;
  const roleVal =
    typeof user.role === "string"
      ? user.role
      : typeof user.userRole === "string"
        ? user.userRole
        : "";
  const primaryRole = normalizeRoleName(roleVal).replace(/^ROLE_/, "");
  if (primaryRole === SYSTEM_ROLES.ADMIN) return true;

  const roles = user.roles || user.authorities || [];
  return (
    Array.isArray(roles) &&
    roles.some(
      (role) =>
        normalizeRoleName(role).replace(/^ROLE_/, "") === SYSTEM_ROLES.ADMIN,
    )
  );
}

export function getUserPermissionSet(user) {
  if (!user || !user.permissions) {
    return new Set();
  }
  if (user.permissions instanceof Set) {
    return user.permissions;
  }
  const raw = Array.isArray(user.permissions) ? user.permissions : [];
  const set = new Set();
  for (const p of raw) {
    if (typeof p === "string") set.add(p);
    else if (p && typeof p.name === "string") set.add(p.name);
  }
  return set;
}

export function hasPermission(user, permission) {
  if (!user || !permission) return false;
  const permissionSet = getUserPermissionSet(user);
  if (permissionSet.has(permission)) return true;
  const mapped = PERMISSIONS[permission];
  if (mapped && permissionSet.has(mapped)) return true;
  for (const [key, val] of Object.entries(PERMISSIONS)) {
    if (val === permission && permissionSet.has(key)) return true;
  }
  if (!hasExplicitPermissions(user) && isSuperAdmin(user)) return true;
  return false;
}

export function hasAnyPermission(user, permissions = []) {
  if (!user) return false;
  if (!Array.isArray(permissions) || permissions.length === 0) return true;
  return permissions.some((permission) => hasPermission(user, permission));
}

export function hasAllPermissions(user, permissions = []) {
  if (!user) return false;
  if (!Array.isArray(permissions) || permissions.length === 0) return true;
  return permissions.every((permission) => hasPermission(user, permission));
}

export function isAdminUser(user) {
  if (!user) return false;
  if (isSuperAdmin(user)) return true;
  const perms = Array.isArray(user.permissions)
    ? user.permissions
    : user.permissions instanceof Set
      ? Array.from(user.permissions)
      : [];
  return perms.some((permission) => {
    const permName =
      typeof permission === "string" ? permission : permission?.name;
    return (
      typeof permName === "string" && ADMIN_PERMISSION_PATTERN.test(permName)
    );
  });
}

export function isAdminAccount(person) {
  if (!person) return false;
  if (person.userRole === false) return true;
  if (isAdminUser(person)) return true;
  const roleVal =
    typeof person.role === "string"
      ? person.role
      : typeof person.userRole === "string"
        ? person.userRole
        : "";
  const role = normalizeRoleName(roleVal).replace(/^ROLE_/, "");
  if (role === SYSTEM_ROLES.ADMIN) return true;
  const roles = person.roles || person.authorities || [];
  if (
    Array.isArray(roles) &&
    roles.some(
      (r) => normalizeRoleName(r).replace(/^ROLE_/, "") === SYSTEM_ROLES.ADMIN,
    )
  ) {
    return true;
  }
  if (person.userRole === true) return false;
  return false;
}

export function isUserRoleCategory(role) {
  if (!role) return true;
  const roleName = normalizeRoleName(
    typeof role === "object" ? role.name : role,
  ).replace(/^ROLE_/, "");
  if (roleName === SYSTEM_ROLES.ADMIN) return false;
  if (roleName === SYSTEM_ROLES.USER) return true;
  const perms = Array.isArray(role?.permissions) ? role.permissions : [];
  const hasAdminPerm = perms.some((p) => {
    const name = typeof p === "string" ? p : p?.name;
    return typeof name === "string" && name.toUpperCase().startsWith("ADMIN_");
  });
  if (hasAdminPerm) return false;
  if (typeof role === "object" && role.userRole !== undefined) {
    return Boolean(role.userRole);
  }
  return true;
}

export function canAssignRole(user, targetRole) {
  if (!user || !targetRole) return false;
  const rawPermissions = Array.isArray(targetRole.permissions)
    ? targetRole.permissions
    : targetRole.permissions instanceof Set
      ? Array.from(targetRole.permissions)
      : [];
  const rolePermissions = rawPermissions
    .map((p) => (typeof p === "string" ? p : p?.name))
    .filter(Boolean);
  return hasAllPermissions(user, rolePermissions);
}
