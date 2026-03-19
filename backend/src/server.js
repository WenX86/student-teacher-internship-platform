import cors from "cors";
import express from "express";
import { createToken, enrichUser, parseToken, serializeUser } from "./lib/auth.js";
import {
  FORM_STATUSES,
  INTERNSHIP_APPLICATION_STATUSES,
  MENTOR_APPLICATION_STATUSES,
  ROLES,
} from "./lib/constants.js";
import { appendLog, createMessage, hashPassword, loadDb, nextId, saveDb } from "./lib/db.js";
import { getDashboard, getReportSummary } from "./lib/stats.js";

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json({ limit: "2mb" }));

function success(res, data) {
  return res.json({ success: true, data });
}

function findStudentByUserId(db, userId) {
  return db.students.find((item) => item.userId === userId) ?? null;
}

function findTeacherByUserId(db, userId) {
  return db.teachers.find((item) => item.userId === userId) ?? null;
}

function getOrganization(db, organizationId) {
  return db.organizations.find((item) => item.id === organizationId) ?? null;
}

function getLatestMentorApplication(db, studentId) {
  return db.mentorApplications
    .filter((item) => item.studentId === studentId)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))[0] ?? null;
}

function getEffectiveTeacher(db, studentId) {
  const relation = db.mentorApplications.find(
    (item) => item.studentId === studentId && item.status === MENTOR_APPLICATION_STATUSES.EFFECTIVE
  );
  return relation ? db.teachers.find((item) => item.id === relation.teacherId) : null;
}

function mapForm(db, form) {
  const student = db.students.find((item) => item.id === form.studentId);
  return {
    ...form,
    studentName: student?.name ?? "",
    studentNo: student?.studentNo ?? "",
    internshipType: student?.internshipType ?? "",
    mentorTeacherName: getEffectiveTeacher(db, form.studentId)?.name ?? "",
  };
}

async function getCurrentContext(req) {
  const header = req.headers.authorization;
  if (!header?.startsWith("Bearer ")) {
    return null;
  }

  const userId = parseToken(header.slice(7));
  if (!userId) {
    return null;
  }

  const db = await loadDb();
  const user = db.users.find((item) => item.id === userId && item.status === "ACTIVE");
  if (!user) {
    return null;
  }

  return {
    db,
    user: enrichUser(db, user),
  };
}

function requireAuth(roles = []) {
  return async (req, res, next) => {
    const context = await getCurrentContext(req);
    if (!context?.user) {
      return res.status(401).json({ message: "未登录或登录已失效" });
    }

    if (roles.length && !roles.includes(context.user.role)) {
      return res.status(403).json({ message: "当前角色无权访问该资源" });
    }

    req.db = context.db;
    req.currentUser = context.user;
    return next();
  };
}

app.get("/api/health", (_req, res) => {
  success(res, {
    ok: true,
    time: new Date().toISOString(),
    stage: "phase-one",
  });
});

app.post("/api/auth/login", async (req, res) => {
  const { account, password } = req.body;
  const db = await loadDb();
  const user = db.users.find((item) => item.account === account && item.status === "ACTIVE");

  if (!user || user.password !== hashPassword(password)) {
    return res.status(401).json({ message: "账号或密码错误" });
  }

  user.lastLoginAt = new Date().toISOString();
  appendLog(db, {
    type: "LOGIN",
    operatorId: user.id,
    action: "用户登录",
    detail: `${user.name} 登录系统`,
  });
  await saveDb(db);

  return success(res, {
    token: createToken(user.id),
    user: serializeUser(enrichUser(db, user)),
  });
});

app.get("/api/auth/me", requireAuth(), async (req, res) => {
  success(res, serializeUser(req.currentUser));
});

app.post("/api/auth/change-password", requireAuth(), async (req, res) => {
  const user = req.db.users.find((item) => item.id === req.currentUser.id);
  user.password = hashPassword(req.body.newPassword);
  user.mustChangePassword = false;
  appendLog(req.db, {
    type: "SECURITY",
    operatorId: user.id,
    action: "修改密码",
    detail: `${user.name} 修改密码`,
  });
  await saveDb(req.db);
  success(res, true);
});

app.get("/api/auth/login-records", requireAuth(), async (req, res) => {
  success(res, req.db.logs.filter((item) => item.type === "LOGIN" && item.operatorId === req.currentUser.id));
});

app.get("/api/dashboard", requireAuth(), async (req, res) => {
  success(res, getDashboard(req.db, req.currentUser));
});

app.get("/api/messages", requireAuth(), async (req, res) => {
  success(
    res,
    req.db.messages
      .filter((item) => item.userId === req.currentUser.id)
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  );
});

app.post("/api/messages/:id/read", requireAuth(), async (req, res) => {
  const message = req.db.messages.find((item) => item.id === req.params.id && item.userId === req.currentUser.id);
  if (!message) {
    return res.status(404).json({ message: "消息不存在" });
  }

  message.read = true;
  await saveDb(req.db);
  success(res, true);
});

app.get("/api/students", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  const data = req.db.students
    .filter((item) => item.collegeId === req.currentUser.collegeId)
    .map((student) => {
      const user = req.db.users.find((item) => item.id === student.userId);
      const mentor = getLatestMentorApplication(req.db, student.id);
      const internship = req.db.internshipApplications.find((item) => item.studentId === student.id);
      return {
        ...student,
        account: user?.account ?? "",
        accountStatus: user?.status ?? "DISABLED",
        mentorStatus: mentor?.status ?? "未申请",
        mentorTeacherName: mentor ? req.db.teachers.find((item) => item.id === mentor.teacherId)?.name ?? "" : "",
        organizationName: internship ? getOrganization(req.db, internship.organizationId)?.name ?? "" : "",
        internshipApplicationStatus: internship?.status ?? "未申请",
      };
    });

  success(res, data);
});

app.post("/api/students", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  if (req.db.students.some((item) => item.studentNo === req.body.studentNo)) {
    return res.status(400).json({ message: "学号已存在" });
  }

  const userId = nextId("user");
  const studentId = nextId("student");

  req.db.users.push({
    id: userId,
    account: req.body.studentNo,
    name: req.body.name,
    role: ROLES.STUDENT,
    password: hashPassword("123456"),
    mustChangePassword: true,
    status: "ACTIVE",
    collegeId: req.currentUser.collegeId,
    lastLoginAt: null,
  });

  req.db.students.push({
    id: studentId,
    userId,
    name: req.body.name,
    studentNo: req.body.studentNo,
    collegeId: req.currentUser.collegeId,
    major: req.body.major,
    className: req.body.className,
    phone: req.body.phone,
    internshipType: req.body.internshipType,
    internshipStatus: "待申请",
    profileCompleted: true,
  });

  appendLog(req.db, {
    type: "OPERATION",
    operatorId: req.currentUser.id,
    action: "新增学生",
    detail: `新增学生 ${req.body.name}`,
  });
  await saveDb(req.db);
  success(res, true);
});

app.post("/api/students/:id/reset-password", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  const student = req.db.students.find((item) => item.id === req.params.id);
  if (!student) {
    return res.status(404).json({ message: "学生不存在" });
  }

  const user = req.db.users.find((item) => item.id === student.userId);
  user.password = hashPassword("123456");
  user.mustChangePassword = true;
  await saveDb(req.db);
  success(res, true);
});

app.patch("/api/students/:id/status", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  const student = req.db.students.find((item) => item.id === req.params.id);
  if (!student) {
    return res.status(404).json({ message: "学生不存在" });
  }

  const user = req.db.users.find((item) => item.id === student.userId);
  user.status = req.body.status;
  await saveDb(req.db);
  success(res, true);
});

app.get("/api/teachers", requireAuth([ROLES.COLLEGE_ADMIN, ROLES.STUDENT]), async (req, res) => {
  const data = req.db.teachers
    .filter((item) => item.collegeId === req.currentUser.collegeId)
    .map((teacher) => {
      const user = req.db.users.find((item) => item.id === teacher.userId);
      const studentCount = req.db.mentorApplications.filter(
        (item) => item.teacherId === teacher.id && item.status === MENTOR_APPLICATION_STATUSES.EFFECTIVE
      ).length;

      return {
        ...teacher,
        account: user?.account ?? "",
        accountStatus: user?.status ?? "DISABLED",
        studentCount,
      };
    });

  success(res, data);
});

app.post("/api/teachers", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  if (req.db.teachers.some((item) => item.employeeNo === req.body.employeeNo)) {
    return res.status(400).json({ message: "工号已存在" });
  }

  const userId = nextId("user");
  const teacherId = nextId("teacher");

  req.db.users.push({
    id: userId,
    account: req.body.employeeNo,
    name: req.body.name,
    role: ROLES.TEACHER,
    password: hashPassword("123456"),
    mustChangePassword: true,
    status: "ACTIVE",
    collegeId: req.currentUser.collegeId,
    lastLoginAt: null,
  });

  req.db.teachers.push({
    id: teacherId,
    userId,
    name: req.body.name,
    employeeNo: req.body.employeeNo,
    collegeId: req.currentUser.collegeId,
    department: req.body.department,
    phone: req.body.phone,
    status: "ACTIVE",
  });

  await saveDb(req.db);
  success(res, true);
});

app.get("/api/organizations", requireAuth([ROLES.COLLEGE_ADMIN, ROLES.STUDENT]), async (req, res) => {
  success(res, req.db.organizations.filter((item) => item.collegeId === req.currentUser.collegeId));
});

app.post("/api/organizations", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  req.db.organizations.unshift({
    id: nextId("org"),
    collegeId: req.currentUser.collegeId,
    ...req.body,
  });
  await saveDb(req.db);
  success(res, true);
});

app.patch("/api/organizations/:id", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  const organization = req.db.organizations.find((item) => item.id === req.params.id);
  if (!organization) {
    return res.status(404).json({ message: "实习单位不存在" });
  }

  Object.assign(organization, req.body);
  await saveDb(req.db);
  success(res, true);
});

app.get("/api/mentor-applications", requireAuth([ROLES.STUDENT, ROLES.TEACHER, ROLES.COLLEGE_ADMIN]), async (req, res) => {
  let list = req.db.mentorApplications;

  if (req.currentUser.role === ROLES.STUDENT) {
    const student = findStudentByUserId(req.db, req.currentUser.id);
    list = list.filter((item) => item.studentId === student?.id);
  }

  if (req.currentUser.role === ROLES.TEACHER) {
    const teacher = findTeacherByUserId(req.db, req.currentUser.id);
    list = list.filter((item) => item.teacherId === teacher?.id);
  }

  success(
    res,
    list.map((item) => ({
      ...item,
      student: req.db.students.find((student) => student.id === item.studentId),
      teacher: req.db.teachers.find((teacher) => teacher.id === item.teacherId),
    }))
  );
});

app.post("/api/mentor-applications", requireAuth([ROLES.STUDENT]), async (req, res) => {
  const student = findStudentByUserId(req.db, req.currentUser.id);
  const teacher = req.db.teachers.find((item) => item.id === req.body.teacherId);

  if (!student || !teacher) {
    return res.status(400).json({ message: "学生或教师信息不存在" });
  }

  req.db.mentorApplications.unshift({
    id: nextId("mentor-app"),
    studentId: student.id,
    teacherId: teacher.id,
    status: MENTOR_APPLICATION_STATUSES.PENDING_TEACHER,
    studentRemark: req.body.studentRemark ?? "",
    teacherRemark: "",
    collegeRemark: "",
    createdAt: new Date().toISOString(),
    teacherReviewedAt: null,
    collegeReviewedAt: null,
    effectiveAt: null,
  });

  createMessage(req.db, {
    userId: teacher.userId,
    type: "待办提醒",
    title: `${student.name} 发起了指导教师申请`,
    content: "请在教师端确认是否接收。",
    link: "/teacher/mentor-requests",
  });

  await saveDb(req.db);
  success(res, true);
});

app.post("/api/mentor-applications/:id/teacher-review", requireAuth([ROLES.TEACHER]), async (req, res) => {
  const teacher = findTeacherByUserId(req.db, req.currentUser.id);
  const item = req.db.mentorApplications.find((row) => row.id === req.params.id && row.teacherId === teacher?.id);
  if (!item) {
    return res.status(404).json({ message: "指导申请不存在" });
  }

  item.teacherRemark = req.body.comment ?? "";
  item.teacherReviewedAt = new Date().toISOString();
  item.status = req.body.approved
    ? MENTOR_APPLICATION_STATUSES.PENDING_COLLEGE
    : MENTOR_APPLICATION_STATUSES.TEACHER_REJECTED;

  const student = req.db.students.find((row) => row.id === item.studentId);
  const collegeAdmin = req.db.users.find(
    (row) => row.role === ROLES.COLLEGE_ADMIN && row.collegeId === req.currentUser.collegeId
  );

  createMessage(req.db, {
    userId: student.userId,
    type: req.body.approved ? "审核结果" : "退回通知",
    title: `指导申请${req.body.approved ? "已获教师确认" : "被教师驳回"}`,
    content: req.body.comment ?? "请查看结果。",
    link: "/student/mentor-applications",
  });

  if (req.body.approved && collegeAdmin) {
    createMessage(req.db, {
      userId: collegeAdmin.id,
      type: "待办提醒",
      title: `${student.name} 的指导关系待学院复核`,
      content: "教师已确认，请完成复核。",
      link: "/college/mentor-relations",
    });
  }

  await saveDb(req.db);
  success(res, true);
});

app.post("/api/mentor-applications/:id/college-review", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  const item = req.db.mentorApplications.find((row) => row.id === req.params.id);
  if (!item) {
    return res.status(404).json({ message: "指导申请不存在" });
  }

  item.collegeRemark = req.body.comment ?? "";
  item.collegeReviewedAt = new Date().toISOString();
  item.status = req.body.approved
    ? MENTOR_APPLICATION_STATUSES.EFFECTIVE
    : MENTOR_APPLICATION_STATUSES.COLLEGE_REJECTED;
  item.effectiveAt = req.body.approved ? new Date().toISOString() : null;

  const student = req.db.students.find((row) => row.id === item.studentId);
  createMessage(req.db, {
    userId: student.userId,
    type: req.body.approved ? "审核结果" : "退回通知",
    title: `指导关系${req.body.approved ? "已正式生效" : "学院复核未通过"}`,
    content: req.body.comment ?? "请查看学院处理意见。",
    link: "/student/mentor-applications",
  });

  await saveDb(req.db);
  success(res, true);
});

app.get("/api/internship-applications", requireAuth([ROLES.STUDENT, ROLES.COLLEGE_ADMIN]), async (req, res) => {
  let list = req.db.internshipApplications;
  if (req.currentUser.role === ROLES.STUDENT) {
    const student = findStudentByUserId(req.db, req.currentUser.id);
    list = list.filter((item) => item.studentId === student?.id);
  }

  success(
    res,
    list.map((item) => ({
      ...item,
      student: req.db.students.find((student) => student.id === item.studentId),
      organization: getOrganization(req.db, item.organizationId),
    }))
  );
});

app.post("/api/internship-applications", requireAuth([ROLES.STUDENT]), async (req, res) => {
  const student = findStudentByUserId(req.db, req.currentUser.id);
  const mentor = getLatestMentorApplication(req.db, student.id);

  if (!mentor || mentor.status !== MENTOR_APPLICATION_STATUSES.EFFECTIVE) {
    return res.status(400).json({ message: "指导关系尚未正式生效，暂不能提交实习申请" });
  }

  req.db.internshipApplications.unshift({
    id: nextId("internship-app"),
    studentId: student.id,
    organizationId: req.body.organizationId,
    status: INTERNSHIP_APPLICATION_STATUSES.PENDING_COLLEGE,
    batchName: req.body.batchName,
    position: req.body.position,
    gradeTarget: req.body.gradeTarget,
    startDate: req.body.startDate,
    endDate: req.body.endDate,
    remark: req.body.remark ?? "",
    attachments: req.body.attachments ?? [],
    organizationConfirmation: "待登记",
    organizationFeedback: "",
    receivedAt: null,
    createdAt: new Date().toISOString(),
    reviewedAt: null,
    reviewComment: "",
  });

  const collegeAdmin = req.db.users.find(
    (row) => row.role === ROLES.COLLEGE_ADMIN && row.collegeId === req.currentUser.collegeId
  );

  if (collegeAdmin) {
    createMessage(req.db, {
      userId: collegeAdmin.id,
      type: "待办提醒",
      title: `${student.name} 提交了实习申请`,
      content: "请核验指导关系并登记单位确认结果。",
      link: "/college/internship-applications",
    });
  }

  await saveDb(req.db);
  success(res, true);
});

app.post("/api/internship-applications/:id/review", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  const application = req.db.internshipApplications.find((item) => item.id === req.params.id);
  if (!application) {
    return res.status(404).json({ message: "实习申请不存在" });
  }

  application.status = req.body.approved
    ? INTERNSHIP_APPLICATION_STATUSES.APPROVED
    : INTERNSHIP_APPLICATION_STATUSES.REJECTED;
  application.organizationConfirmation = req.body.organizationConfirmation ?? application.organizationConfirmation;
  application.organizationFeedback = req.body.organizationFeedback ?? application.organizationFeedback;
  application.receivedAt = req.body.receivedAt ?? application.receivedAt;
  application.reviewComment = req.body.comment ?? "";
  application.reviewedAt = new Date().toISOString();

  const student = req.db.students.find((item) => item.id === application.studentId);
  if (req.body.approved) {
    student.internshipStatus = "实习中";
  }

  createMessage(req.db, {
    userId: student.userId,
    type: req.body.approved ? "审核结果" : "退回通知",
    title: `实习申请${req.body.approved ? "已通过" : "被退回"}`,
    content: req.body.comment ?? "请查看学院审批意见。",
    link: "/student/internship-application",
  });

  await saveDb(req.db);
  success(res, true);
});

app.get("/api/form-templates", requireAuth(), async (req, res) => {
  let templates = req.db.formTemplates;
  if (req.currentUser.role === ROLES.STUDENT) {
    const student = findStudentByUserId(req.db, req.currentUser.id);
    templates = templates.filter((item) => item.applicableTypes.includes(student?.internshipType));
  }

  success(res, templates);
});

app.get("/api/forms", requireAuth([ROLES.STUDENT, ROLES.TEACHER, ROLES.COLLEGE_ADMIN]), async (req, res) => {
  let forms = req.db.formInstances;

  if (req.currentUser.role === ROLES.STUDENT) {
    const student = findStudentByUserId(req.db, req.currentUser.id);
    forms = forms.filter((item) => item.studentId === student?.id);
  }

  if (req.currentUser.role === ROLES.TEACHER) {
    const teacher = findTeacherByUserId(req.db, req.currentUser.id);
    const studentIds = req.db.mentorApplications
      .filter((item) => item.teacherId === teacher?.id && item.status === MENTOR_APPLICATION_STATUSES.EFFECTIVE)
      .map((item) => item.studentId);
    forms = forms.filter((item) => studentIds.includes(item.studentId));
  }

  if (req.query.category) {
    forms = forms.filter((item) => item.category === req.query.category);
  }

  success(
    res,
    forms.map((item) => mapForm(req.db, item)).sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
  );
});

app.post("/api/forms", requireAuth([ROLES.STUDENT]), async (req, res) => {
  const student = findStudentByUserId(req.db, req.currentUser.id);
  const template = req.db.formTemplates.find((item) => item.code === req.body.templateCode);
  if (!student || !template) {
    return res.status(400).json({ message: "模板不存在或学生信息异常" });
  }

  req.db.formInstances.unshift({
    id: nextId("form"),
    studentId: student.id,
    templateCode: template.code,
    templateName: template.name,
    category: template.category,
    status: req.body.submit ? FORM_STATUSES.TEACHER_REVIEWING : FORM_STATUSES.DRAFT,
    version: 1,
    content: req.body.content,
    attachments: req.body.attachments ?? [],
    teacherComment: "",
    collegeComment: "",
    score: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    submittedAt: req.body.submit ? new Date().toISOString() : null,
    teacherReviewedAt: null,
    collegeReviewedAt: null,
    history: []
  });

  const teacher = getEffectiveTeacher(req.db, student.id);
  if (req.body.submit && teacher) {
    createMessage(req.db, {
      userId: teacher.userId,
      type: "待办提醒",
      title: `${student.name} 提交了 ${template.name}`,
      content: "请在教师端完成审核。",
      link: "/teacher/reviews"
    });
  }

  await saveDb(req.db);
  success(res, true);
});

app.put("/api/forms/:id", requireAuth([ROLES.STUDENT]), async (req, res) => {
  const student = findStudentByUserId(req.db, req.currentUser.id);
  const form = req.db.formInstances.find((item) => item.id === req.params.id && item.studentId === student?.id);
  if (!form) {
    return res.status(404).json({ message: "表单不存在" });
  }

  form.history.unshift({
    version: form.version,
    status: form.status,
    content: form.content,
    updatedAt: form.updatedAt,
  });
  form.version += 1;
  form.content = req.body.content;
  form.attachments = req.body.attachments ?? [];
  form.updatedAt = new Date().toISOString();
  form.status = req.body.submit ? FORM_STATUSES.TEACHER_REVIEWING : FORM_STATUSES.DRAFT;
  form.submittedAt = req.body.submit ? new Date().toISOString() : form.submittedAt;
  form.teacherComment = "";
  form.collegeComment = "";
  form.score = null;

  const teacher = getEffectiveTeacher(req.db, student.id);
  if (req.body.submit && teacher) {
    createMessage(req.db, {
      userId: teacher.userId,
      type: "待办提醒",
      title: `${student.name} 重新提交了 ${form.templateName}`,
      content: "请重新审核该材料。",
      link: "/teacher/reviews",
    });
  }

  await saveDb(req.db);
  success(res, true);
});

app.post("/api/forms/:id/teacher-review", requireAuth([ROLES.TEACHER]), async (req, res) => {
  const teacher = findTeacherByUserId(req.db, req.currentUser.id);
  const activeStudentIds = req.db.mentorApplications
    .filter((item) => item.teacherId === teacher?.id && item.status === MENTOR_APPLICATION_STATUSES.EFFECTIVE)
    .map((item) => item.studentId);
  const form = req.db.formInstances.find((item) => item.id === req.params.id && activeStudentIds.includes(item.studentId));
  if (!form) {
    return res.status(404).json({ message: "表单不存在" });
  }

  form.teacherComment = req.body.comment ?? "";
  form.score = req.body.score ?? form.score;
  form.teacherReviewedAt = new Date().toISOString();
  form.updatedAt = new Date().toISOString();
  form.status = req.body.approved ? FORM_STATUSES.COLLEGE_REVIEWING : FORM_STATUSES.TEACHER_RETURNED;

  const student = req.db.students.find((item) => item.id === form.studentId);
  createMessage(req.db, {
    userId: student.userId,
    type: req.body.approved ? "审核结果" : "退回通知",
    title: `${form.templateName}${req.body.approved ? "已通过教师审核" : "被教师退回"}`,
    content: req.body.comment ?? "请查看教师意见。",
    link: "/student/tasks",
  });

  if (req.body.approved) {
    const collegeAdmin = req.db.users.find(
      (item) => item.role === ROLES.COLLEGE_ADMIN && item.collegeId === req.currentUser.collegeId
    );
    if (collegeAdmin) {
      createMessage(req.db, {
        userId: collegeAdmin.id,
        type: "待办提醒",
        title: `${student.name} 的 ${form.templateName} 待归档`,
        content: "教师审核已通过，请完成学院终审。",
        link: "/college/archive",
      });
    }
  }

  await saveDb(req.db);
  success(res, true);
});

app.post("/api/forms/:id/college-review", requireAuth([ROLES.COLLEGE_ADMIN]), async (req, res) => {
  const form = req.db.formInstances.find((item) => item.id === req.params.id);
  if (!form) {
    return res.status(404).json({ message: "表单不存在" });
  }

  form.collegeComment = req.body.comment ?? "";
  form.collegeReviewedAt = new Date().toISOString();
  form.updatedAt = new Date().toISOString();
  form.status = req.body.approved ? FORM_STATUSES.ARCHIVED : FORM_STATUSES.COLLEGE_RETURNED;

  const student = req.db.students.find((item) => item.id === form.studentId);
  createMessage(req.db, {
    userId: student.userId,
    type: req.body.approved ? "审核结果" : "退回通知",
    title: `${form.templateName}${req.body.approved ? "已归档" : "被学院退回"}`,
    content: req.body.comment ?? "请查看学院意见。",
    link: "/student/tasks",
  });

  await saveDb(req.db);
  success(res, true);
});

app.get("/api/guidance-records", requireAuth([ROLES.TEACHER]), async (req, res) => {
  const teacher = findTeacherByUserId(req.db, req.currentUser.id);
  success(
    res,
    req.db.guidanceRecords
      .filter((item) => item.teacherId === teacher?.id)
      .map((item) => ({
        ...item,
        student: req.db.students.find((student) => student.id === item.studentId),
      }))
  );
});

app.post("/api/guidance-records", requireAuth([ROLES.TEACHER]), async (req, res) => {
  const teacher = findTeacherByUserId(req.db, req.currentUser.id);
  req.db.guidanceRecords.unshift({
    id: nextId("guide"),
    teacherId: teacher.id,
    ...req.body,
  });
  await saveDb(req.db);
  success(res, true);
});

app.get("/api/evaluations", requireAuth([ROLES.STUDENT, ROLES.TEACHER, ROLES.COLLEGE_ADMIN]), async (req, res) => {
  let evaluations = req.db.evaluations;

  if (req.currentUser.role === ROLES.STUDENT) {
    const student = findStudentByUserId(req.db, req.currentUser.id);
    evaluations = evaluations.filter((item) => item.studentId === student?.id);
  }

  if (req.currentUser.role === ROLES.TEACHER) {
    const teacher = findTeacherByUserId(req.db, req.currentUser.id);
    evaluations = evaluations.filter((item) => item.teacherId === teacher?.id);
  }

  success(
    res,
    evaluations.map((item) => ({
      ...item,
      student: req.db.students.find((student) => student.id === item.studentId),
    }))
  );
});

app.post("/api/evaluations", requireAuth([ROLES.TEACHER]), async (req, res) => {
  const teacher = findTeacherByUserId(req.db, req.currentUser.id);
  const existing = req.db.evaluations.find(
    (item) => item.teacherId === teacher?.id && item.studentId === req.body.studentId
  );

  if (existing) {
    Object.assign(existing, req.body, {
      teacherId: teacher.id,
      submittedToCollege: true,
    });
  } else {
    req.db.evaluations.unshift({
      id: nextId("eval"),
      teacherId: teacher.id,
      submittedToCollege: true,
      confirmedByCollege: false,
      ...req.body,
    });
  }

  await saveDb(req.db);
  success(res, true);
});

app.get("/api/reports/summary", requireAuth([ROLES.COLLEGE_ADMIN, ROLES.SUPER_ADMIN]), async (req, res) => {
  success(res, getReportSummary(req.db));
});

app.get("/api/admin/college-applications", requireAuth([ROLES.SUPER_ADMIN]), async (req, res) => {
  success(res, req.db.collegeApplications);
});

app.post("/api/admin/college-applications/:id/review", requireAuth([ROLES.SUPER_ADMIN]), async (req, res) => {
  const item = req.db.collegeApplications.find((row) => row.id === req.params.id);
  if (!item) {
    return res.status(404).json({ message: "入驻申请不存在" });
  }

  item.status = req.body.approved ? "已通过" : "已驳回";
  item.reviewComment = req.body.comment ?? "";
  await saveDb(req.db);
  success(res, true);
});

app.get("/api/admin/basic-data", requireAuth([ROLES.SUPER_ADMIN]), async (req, res) => {
  success(res, {
    colleges: req.db.colleges,
    roles: Object.values(ROLES),
    formStatuses: Object.values(FORM_STATUSES),
  });
});

app.get("/api/admin/logs", requireAuth([ROLES.SUPER_ADMIN]), async (req, res) => {
  success(res, req.db.logs.slice(0, 50));
});

app.listen(PORT, () => {
  console.log(`phase-one backend listening on http://localhost:${PORT}`);
});
