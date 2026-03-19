import http from "node:http";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { FORM_STATUSES, GUIDANCE_STATUSES, INTERNSHIP_APPLICATION_STATUSES, INTERNSHIP_TYPES, ROLES } from "./backend-constants.js";
import { hashPassword, store } from "./backend-store.js";
import {
  buildDashboard,
  getCurrentInternship,
  getOverviewReports,
  getRoleProfile,
  getStudentById,
  getStudentByUserId,
  getTeacherByUserId,
  getUserById,
  getUserFromAuthorization,
  issueToken,
  nowIso,
  sanitizeUser,
  serializeForm,
  serializeGuidance,
  serializeInternship,
  serializeMessage,
  sortByCreatedDesc,
  addAudit
} from "./backend-helpers.js";
import {
  createGuidanceRecord,
  createGuidanceRequest,
  createInternshipApplication,
  createOrUpdateForm,
  createStudentByAdmin,
  createTeacherByAdmin,
  createUnitByAdmin,
  getStudentTemplates,
  requestFormChange,
  reviewFormByAdmin,
  reviewFormByTeacher,
  reviewGuidanceByAdmin,
  reviewGuidanceByTeacher,
  reviewInternshipByAdmin,
  updateEntity
} from "./backend-business.js";

const ROOT_DIR = path.resolve(".");
const FRONTEND_DIR = path.join(ROOT_DIR, "frontend");
const PORT = Number(process.env.PORT || 3000);

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Allow-Methods": "GET,POST,PUT,DELETE,OPTIONS",
    "Content-Type": "application/json; charset=utf-8"
  });
  res.end(JSON.stringify(payload));
}

function sendError(res, statusCode, message) {
  sendJson(res, statusCode, { error: message });
}

async function readJson(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  if (!chunks.length) {
    return {};
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new Error("请求体不是合法 JSON。");
  }
}

function matchPath(pathname, pattern) {
  const left = pathname.split("/").filter(Boolean);
  const right = pattern.split("/").filter(Boolean);
  if (left.length !== right.length) {
    return null;
  }
  const params = {};
  for (let index = 0; index < left.length; index += 1) {
    if (right[index].startsWith(":")) {
      params[right[index].slice(1)] = left[index];
    } else if (right[index] !== left[index]) {
      return null;
    }
  }
  return params;
}

function requireAuth(res, user) {
  if (!user) {
    sendError(res, 401, "请先登录。");
    return false;
  }
  return true;
}

function requireRole(res, user, roles) {
  if (!roles.includes(user.role)) {
    sendError(res, 403, "当前角色无权限访问。");
    return false;
  }
  return true;
}

async function serveFrontend(res, pathname) {
  const fileName = pathname === "/" ? "index.html" : pathname.replace(/^\//, "");
  const allowList = new Set(["index.html", "app.js", "styles.css"]);
  if (!allowList.has(fileName)) {
    sendError(res, 404, "页面不存在。");
    return;
  }
  try {
    const content = await readFile(path.join(FRONTEND_DIR, fileName));
    const contentType =
      fileName.endsWith(".html") ? "text/html; charset=utf-8" :
      fileName.endsWith(".js") ? "text/javascript; charset=utf-8" :
      "text/css; charset=utf-8";
    res.writeHead(200, { "Content-Type": contentType });
    res.end(content);
  } catch {
    sendError(res, 404, "前端资源不存在。");
  }
}

function getBootstrap(user) {
  return {
    user: sanitizeUser(user),
    profile: getRoleProfile(user),
    dashboard: buildDashboard(user)
  };
}

async function handleApi(req, res, url, user) {
  const pathname = url.pathname;

  if (req.method === "OPTIONS") {
    sendJson(res, 200, { ok: true });
    return;
  }

  try {
    if (pathname === "/api/auth/login" && req.method === "POST") {
      const body = await readJson(req);
      const current = store.users.find((item) => item.account === body.account);
      if (!current || current.passwordHash !== hashPassword(String(body.password || ""))) {
        sendError(res, 401, "账号或密码错误。");
        return;
      }
      current.lastLoginAt = nowIso();
      const token = issueToken(current.id);
      addAudit(current.id, "LOGIN", "user", current.id, "用户登录。");
      sendJson(res, 200, { token, ...getBootstrap(current) });
      return;
    }

    if (pathname === "/api/auth/logout" && req.method === "POST") {
      if (!requireAuth(res, user)) return;
      const token = String(req.headers.authorization || "").replace("Bearer ", "");
      const index = store.sessions.findIndex((item) => item.token === token);
      if (index >= 0) {
        store.sessions.splice(index, 1);
      }
      addAudit(user.id, "LOGOUT", "user", user.id, "用户退出登录。");
      sendJson(res, 200, { ok: true });
      return;
    }

    if (pathname === "/api/auth/me" && req.method === "GET") {
      if (!requireAuth(res, user)) return;
      sendJson(res, 200, getBootstrap(user));
      return;
    }

    if (pathname === "/api/auth/change-password" && req.method === "POST") {
      if (!requireAuth(res, user)) return;
      const body = await readJson(req);
      if (user.passwordHash !== hashPassword(String(body.oldPassword || ""))) {
        sendError(res, 400, "旧密码错误。");
        return;
      }
      user.passwordHash = hashPassword(String(body.newPassword || ""));
      user.mustChangePassword = false;
      addAudit(user.id, "CHANGE_PASSWORD", "user", user.id, "用户修改密码。");
      sendJson(res, 200, { ok: true });
      return;
    }

    if (pathname === "/api/messages" && req.method === "GET") {
      if (!requireAuth(res, user)) return;
      sendJson(res, 200, sortByCreatedDesc(store.messages.filter((item) => item.recipientUserId === user.id)).map(serializeMessage));
      return;
    }

    const messageParams = matchPath(pathname, "/api/messages/:id/read");
    if (messageParams && req.method === "POST") {
      if (!requireAuth(res, user)) return;
      const message = store.messages.find((item) => item.id === messageParams.id && item.recipientUserId === user.id);
      if (!message) {
        sendError(res, 404, "消息不存在。");
        return;
      }
      message.read = true;
      sendJson(res, 200, serializeMessage(message));
      return;
    }

    if (pathname === "/api/dashboard" && req.method === "GET") {
      if (!requireAuth(res, user)) return;
      sendJson(res, 200, buildDashboard(user));
      return;
    }

    if (pathname === "/api/student/profile" && req.method === "PUT") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      const body = await readJson(req);
      const profile = getStudentByUserId(user.id);
      updateEntity(profile, body, ["phone", "email", "internshipType", "internshipBatch"]);
      addAudit(user.id, "UPDATE_PROFILE", "student", profile.id, "学生更新个人信息。");
      sendJson(res, 200, profile);
      return;
    }

    if (pathname === "/api/student/teachers" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      const student = getStudentByUserId(user.id);
      const query = String(url.searchParams.get("query") || "");
      const items = store.teachers.filter((item) => item.collegeId === student.collegeId).filter((item) => !query || item.name.includes(query) || item.teacherNo.includes(query));
      sendJson(res, 200, items);
      return;
    }

    if (pathname === "/api/student/guidance-requests" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      const student = getStudentByUserId(user.id);
      sendJson(res, 200, sortByCreatedDesc(store.guidanceRelations.filter((item) => item.studentId === student.id).map(serializeGuidance)));
      return;
    }

    if (pathname === "/api/student/guidance-requests" && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      sendJson(res, 201, createGuidanceRequest(user, await readJson(req)));
      return;
    }

    if (pathname === "/api/student/internship-units" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      const student = getStudentByUserId(user.id);
      sendJson(res, 200, store.internshipUnits.filter((item) => item.collegeId === student.collegeId));
      return;
    }

    if (pathname === "/api/student/internship-applications" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      const student = getStudentByUserId(user.id);
      sendJson(res, 200, sortByCreatedDesc(store.internshipApplications.filter((item) => item.studentId === student.id).map(serializeInternship)));
      return;
    }

    if (pathname === "/api/student/internship-applications" && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      sendJson(res, 201, createInternshipApplication(user, await readJson(req)));
      return;
    }

    if (pathname === "/api/student/forms" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      sendJson(res, 200, getStudentTemplates(user));
      return;
    }

    if (pathname === "/api/student/forms" && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      sendJson(res, 200, createOrUpdateForm(user, await readJson(req)));
      return;
    }

    const studentChangeParams = matchPath(pathname, "/api/student/forms/:id/change-request");
    if (studentChangeParams && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.STUDENT])) return;
      sendJson(res, 200, requestFormChange(user, studentChangeParams.id, await readJson(req)));
      return;
    }

    if (pathname === "/api/teacher/students" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.TEACHER])) return;
      const teacher = getTeacherByUserId(user.id);
      const items = store.guidanceRelations.filter((item) => item.teacherId === teacher.id && item.status === GUIDANCE_STATUSES.EFFECTIVE).map((relation) => {
        const student = getStudentById(relation.studentId);
        return {
          relationId: relation.id,
          studentId: student.id,
          studentName: student.name,
          studentNo: student.studentNo,
          major: student.major,
          className: student.className,
          internshipType: student.internshipType
        };
      });
      sendJson(res, 200, items);
      return;
    }

    if (pathname === "/api/teacher/guidance-requests" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.TEACHER])) return;
      const teacher = getTeacherByUserId(user.id);
      sendJson(res, 200, sortByCreatedDesc(store.guidanceRelations.filter((item) => item.teacherId === teacher.id).map(serializeGuidance)));
      return;
    }

    const teacherGuidanceParams = matchPath(pathname, "/api/teacher/guidance-requests/:id/action");
    if (teacherGuidanceParams && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.TEACHER])) return;
      sendJson(res, 200, reviewGuidanceByTeacher(user, teacherGuidanceParams.id, await readJson(req)));
      return;
    }

    if (pathname === "/api/teacher/forms" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.TEACHER])) return;
      const teacher = getTeacherByUserId(user.id);
      sendJson(res, 200, sortByCreatedDesc(store.formInstances.filter((item) => item.teacherId === teacher.id).map(serializeForm)));
      return;
    }

    const teacherFormParams = matchPath(pathname, "/api/teacher/forms/:id/review");
    if (teacherFormParams && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.TEACHER])) return;
      sendJson(res, 200, reviewFormByTeacher(user, teacherFormParams.id, await readJson(req)));
      return;
    }

    if (pathname === "/api/teacher/guidance-records" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.TEACHER])) return;
      const teacher = getTeacherByUserId(user.id);
      sendJson(res, 200, sortByCreatedDesc(store.guidanceRecords.filter((item) => item.teacherId === teacher.id).map((item) => ({ ...item, studentName: getStudentById(item.studentId)?.name || "" }))));
      return;
    }

    if (pathname === "/api/teacher/guidance-records" && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.TEACHER])) return;
      sendJson(res, 201, createGuidanceRecord(user, await readJson(req)));
      return;
    }

    if (pathname === "/api/admin/students" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, store.students.filter((item) => item.collegeId === user.collegeId));
      return;
    }

    if (pathname === "/api/admin/students" && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 201, createStudentByAdmin(user, await readJson(req)));
      return;
    }

    const adminStudentParams = matchPath(pathname, "/api/admin/students/:id");
    if (adminStudentParams && req.method === "PUT") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      const student = store.students.find((item) => item.id === adminStudentParams.id && item.collegeId === user.collegeId);
      if (!student) {
        sendError(res, 404, "学生不存在。");
        return;
      }
      updateEntity(student, await readJson(req), ["name", "major", "className", "internshipType", "internshipBatch", "phone", "email", "status"]);
      addAudit(user.id, "UPDATE_STUDENT", "student", student.id, `更新学生 ${student.name}。`);
      sendJson(res, 200, student);
      return;
    }

    const adminStudentReset = matchPath(pathname, "/api/admin/students/:id/reset-password");
    if (adminStudentReset && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      const student = store.students.find((item) => item.id === adminStudentReset.id && item.collegeId === user.collegeId);
      if (!student) {
        sendError(res, 404, "学生不存在。");
        return;
      }
      const targetUser = getUserById(student.userId);
      targetUser.passwordHash = hashPassword("Student@123");
      targetUser.mustChangePassword = true;
      addAudit(user.id, "RESET_STUDENT_PASSWORD", "student", student.id, `重置学生 ${student.name} 密码。`);
      sendJson(res, 200, { ok: true, defaultPassword: "Student@123" });
      return;
    }

    if (pathname === "/api/admin/teachers" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, store.teachers.filter((item) => item.collegeId === user.collegeId));
      return;
    }

    if (pathname === "/api/admin/teachers" && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 201, createTeacherByAdmin(user, await readJson(req)));
      return;
    }

    const adminTeacherParams = matchPath(pathname, "/api/admin/teachers/:id");
    if (adminTeacherParams && req.method === "PUT") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      const teacher = store.teachers.find((item) => item.id === adminTeacherParams.id && item.collegeId === user.collegeId);
      if (!teacher) {
        sendError(res, 404, "教师不存在。");
        return;
      }
      updateEntity(teacher, await readJson(req), ["name", "department", "phone", "title", "status"]);
      addAudit(user.id, "UPDATE_TEACHER", "teacher", teacher.id, `更新教师 ${teacher.name}。`);
      sendJson(res, 200, teacher);
      return;
    }

    const adminTeacherReset = matchPath(pathname, "/api/admin/teachers/:id/reset-password");
    if (adminTeacherReset && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      const teacher = store.teachers.find((item) => item.id === adminTeacherReset.id && item.collegeId === user.collegeId);
      if (!teacher) {
        sendError(res, 404, "教师不存在。");
        return;
      }
      const targetUser = getUserById(teacher.userId);
      targetUser.passwordHash = hashPassword("Teacher@123");
      targetUser.mustChangePassword = true;
      addAudit(user.id, "RESET_TEACHER_PASSWORD", "teacher", teacher.id, `重置教师 ${teacher.name} 密码。`);
      sendJson(res, 200, { ok: true, defaultPassword: "Teacher@123" });
      return;
    }

    if (pathname === "/api/admin/units" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, store.internshipUnits.filter((item) => item.collegeId === user.collegeId));
      return;
    }

    if (pathname === "/api/admin/units" && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 201, createUnitByAdmin(user, await readJson(req)));
      return;
    }

    const unitParams = matchPath(pathname, "/api/admin/units/:id");
    if (unitParams && req.method === "PUT") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      const unit = store.internshipUnits.find((item) => item.id === unitParams.id && item.collegeId === user.collegeId);
      if (!unit) {
        sendError(res, 404, "实习单位不存在。");
        return;
      }
      updateEntity(unit, await readJson(req), ["name", "address", "contactName", "contactPhone", "unitType", "cooperationStatus", "yearsAccepted", "evaluation"]);
      addAudit(user.id, "UPDATE_UNIT", "unit", unit.id, `更新单位 ${unit.name}。`);
      sendJson(res, 200, unit);
      return;
    }

    if (pathname === "/api/admin/guidance-requests" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, sortByCreatedDesc(store.guidanceRelations.filter((item) => item.collegeId === user.collegeId).map(serializeGuidance)));
      return;
    }

    const adminGuidanceParams = matchPath(pathname, "/api/admin/guidance-requests/:id/review");
    if (adminGuidanceParams && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, reviewGuidanceByAdmin(user, adminGuidanceParams.id, await readJson(req)));
      return;
    }

    if (pathname === "/api/admin/internship-applications" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, sortByCreatedDesc(store.internshipApplications.filter((item) => item.collegeId === user.collegeId).map(serializeInternship)));
      return;
    }

    const adminInternshipParams = matchPath(pathname, "/api/admin/internship-applications/:id/review");
    if (adminInternshipParams && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, reviewInternshipByAdmin(user, adminInternshipParams.id, await readJson(req)));
      return;
    }

    if (pathname === "/api/admin/forms" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, sortByCreatedDesc(store.formInstances.filter((item) => item.collegeId === user.collegeId).map(serializeForm)));
      return;
    }

    const adminFormParams = matchPath(pathname, "/api/admin/forms/:id/archive");
    if (adminFormParams && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, reviewFormByAdmin(user, adminFormParams.id, await readJson(req)));
      return;
    }

    if (pathname === "/api/admin/reports" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.COLLEGE_ADMIN])) return;
      sendJson(res, 200, getOverviewReports(user.collegeId));
      return;
    }

    if (pathname === "/api/super/college-applications" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.SUPER_ADMIN])) return;
      sendJson(res, 200, sortByCreatedDesc(store.collegeApplications));
      return;
    }

    const collegeApplicationParams = matchPath(pathname, "/api/super/college-applications/:id/review");
    if (collegeApplicationParams && req.method === "POST") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.SUPER_ADMIN])) return;
      const item = store.collegeApplications.find((entry) => entry.id === collegeApplicationParams.id);
      if (!item) {
        sendError(res, 404, "学院入驻申请不存在。");
        return;
      }
      const body = await readJson(req);
      item.status = body.action === "approve" ? "approved" : "rejected";
      item.reviewedAt = nowIso();
      item.reviewComment = body.comment || "";
      addAudit(user.id, "SUPER_REVIEW_COLLEGE_APPLICATION", "college_application", item.id, `超级管理员处理学院入驻申请：${body.action}`);
      sendJson(res, 200, item);
      return;
    }

    if (pathname === "/api/super/audit-logs" && req.method === "GET") {
      if (!requireAuth(res, user) || !requireRole(res, user, [ROLES.SUPER_ADMIN])) return;
      sendJson(res, 200, sortByCreatedDesc(store.auditLogs));
      return;
    }

    sendError(res, 404, "接口不存在。");
  } catch (error) {
    sendError(res, 400, error.message || "请求处理失败。");
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  const user = getUserFromAuthorization(req.headers.authorization);
  if (url.pathname.startsWith("/api/")) {
    await handleApi(req, res, url, user);
    return;
  }
  await serveFrontend(res, url.pathname);
});

server.listen(PORT, () => {
  console.log(`Teacher internship platform running at http://localhost:${PORT}`);
});
