import { ROLES } from "./constants.js";

export function createToken(userId) {
  return Buffer.from(JSON.stringify({ userId }), "utf8").toString("base64url");
}

export function parseToken(token) {
  try {
    const content = Buffer.from(token, "base64url").toString("utf8");
    const parsed = JSON.parse(content);
    return parsed.userId;
  } catch {
    return null;
  }
}

export function enrichUser(db, user) {
  if (!user) {
    return null;
  }

  if (user.role === ROLES.STUDENT) {
    return {
      ...user,
      profile: db.students.find((item) => item.userId === user.id) ?? null,
    };
  }

  if (user.role === ROLES.TEACHER) {
    return {
      ...user,
      profile: db.teachers.find((item) => item.userId === user.id) ?? null,
    };
  }

  if (user.role === ROLES.COLLEGE_ADMIN) {
    return {
      ...user,
      profile: db.colleges.find((item) => item.id === user.collegeId) ?? null,
    };
  }

  return {
    ...user,
    profile: null,
  };
}

export function serializeUser(user) {
  if (!user) {
    return null;
  }

  return {
    id: user.id,
    account: user.account,
    name: user.name,
    role: user.role,
    collegeId: user.collegeId,
    mustChangePassword: user.mustChangePassword,
    lastLoginAt: user.lastLoginAt,
    profile: user.profile,
  };
}
