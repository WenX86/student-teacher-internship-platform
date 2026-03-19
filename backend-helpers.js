import crypto from "node:crypto";
import {
  FORM_STATUSES,
  FORM_STATUS_LABELS,
  FORM_TEMPLATES,
  GUIDANCE_STATUSES,
  GUIDANCE_STATUS_LABELS,
  INTERNSHIP_APPLICATION_STATUSES,
  INTERNSHIP_APPLICATION_STATUS_LABELS,
  INTERNSHIP_TYPES,
  MESSAGE_TYPE_LABELS,
  ROLES
} from "./backend-constants.js";
import { store } from "./backend-store.js";

export function nowIso() {
  return new Date().toISOString();
}

export function createId(prefix) {
  return `${prefix}-${crypto.randomUUID().slice(0, 8)}`;
}

export function issueToken(userId) {
  const token = crypto.randomUUID().replace(/-/g, "");
  store.sessions.unshift({
    token,
    userId,
    createdAt: nowIso()
  });
  return token;
}

export function getUserById(userId) {
  return store.users.find((item) => item.id === userId) || null;
}

export function getCollegeById(collegeId) {
  return store.colleges.find((item) => item.id === collegeId) || null;
}

export function getStudentById(studentId) {
  return store.students.find((item) => item.id === studentId) || null;
}

export function getTeacherById(teacherId) {
  return store.teachers.find((item) => item.id === teacherId) || null;
}

export function getStudentByUserId(userId) {
  return store.students.find((item) => item.userId === userId) || null;
}

export function getTeacherByUserId(userId) {
  return store.teachers.find((item) => item.userId === userId) || null;
}

export function getTeacherUserId(teacherId) {
  return getTeacherById(teacherId)?.userId || null;
}

export function getRoleProfile(user) {
  if (!user) {
    return null;
  }
  if (user.role === ROLES.STUDENT) {
    return getStudentByUserId(user.id);
  }
  if (user.role === ROLES.TEACHER) {
    return getTeacherByUserId(user.id);
  }
  if (user.role === ROLES.COLLEGE_ADMIN) {
    return getCollegeById(user.collegeId);
  }
  return null;
}

export function sanitizeUser(user) {
  return {
    id: user.id,
    account: user.account,
    name: user.name,
    role: user.role,
    collegeId: user.collegeId,
    mustChangePassword: user.mustChangePassword,
    lastLoginAt: user.lastLoginAt,
    profile: getRoleProfile(user)
  };
}

export function addAudit(actorUserId, action, entityType, entityId, detail) {
  store.auditLogs.unshift({
    id: createId("audit"),
    actorUserId,
    action,
    entityType,
    entityId,
    detail,
    createdAt: nowIso()
  });
}

export function addMessage(recipientUserId, type, title, content, relatedType, relatedId, link) {
  if (!recipientUserId) {
    return;
  }
  store.messages.unshift({
    id: createId("message"),
    recipientUserId,
    type,
    title,
    content,
    relatedType,
    relatedId,
    link,
    read: false,
    createdAt: nowIso()
  });
}

export function getUserFromAuthorization(headerValue) {
  const token = String(headerValue || "").replace("Bearer ", "");
  if (!token) {
    return null;
  }
  const session = store.sessions.find((item) => item.token === token);
  return session ? getUserById(session.userId) : null;
}

export function getEffectiveGuidance(studentId) {
  return store.guidanceRelations.find(
    (item) => item.studentId === studentId && item.status === GUIDANCE_STATUSES.EFFECTIVE
  ) || null;
}

export function getPendingGuidance(studentId) {
  return store.guidanceRelations.find(
    (item) =>
      item.studentId === studentId &&
      [GUIDANCE_STATUSES.PENDING_TEACHER, GUIDANCE_STATUSES.PENDING_COLLEGE].includes(item.status)
  ) || null;
}

export function getCurrentInternship(studentId) {
  return store.internshipApplications.find(
    (item) =>
      item.studentId === studentId &&
      [
        INTERNSHIP_APPLICATION_STATUSES.SUBMITTED,
        INTERNSHIP_APPLICATION_STATUSES.APPROVED_PENDING_EXTERNAL,
        INTERNSHIP_APPLICATION_STATUSES.ACTIVE
      ].includes(item.status)
  ) || null;
}

export function getAllowedTemplates(internshipType) {
  return FORM_TEMPLATES.filter(
    (item) => item.internshipType === INTERNSHIP_TYPES.ALL || item.internshipType === internshipType
  );
}

export function serializeGuidance(item) {
  const student = getStudentById(item.studentId);
  const teacher = getTeacherById(item.teacherId);
  return {
    ...item,
    statusLabel: GUIDANCE_STATUS_LABELS[item.status],
    studentName: student?.name || "",
    studentNo: student?.studentNo || "",
    teacherName: teacher?.name || "",
    teacherNo: teacher?.teacherNo || ""
  };
}

export function serializeInternship(item) {
  const student = getStudentById(item.studentId);
  const unit = store.internshipUnits.find((entry) => entry.id === item.unitId) || null;
  return {
    ...item,
    statusLabel: INTERNSHIP_APPLICATION_STATUS_LABELS[item.status],
    studentName: student?.name || "",
    studentNo: student?.studentNo || "",
    unitName: unit?.name || item.newUnitName || ""
  };
}

export function serializeForm(item) {
  const student = getStudentById(item.studentId);
  const teacher = getTeacherById(item.teacherId);
  const template = FORM_TEMPLATES.find((entry) => entry.code === item.templateCode) || null;
  return {
    ...item,
    templateName: template?.name || item.templateCode,
    category: template?.category || "",
    statusLabel: FORM_STATUS_LABELS[item.status],
    studentName: student?.name || "",
    studentNo: student?.studentNo || "",
    teacherName: teacher?.name || ""
  };
}

export function serializeMessage(item) {
  return {
    ...item,
    typeLabel: MESSAGE_TYPE_LABELS[item.type]
  };
}

export function sortByCreatedDesc(items) {
  return [...items].sort((left, right) => String(right.createdAt || right.updatedAt || "").localeCompare(String(left.createdAt || left.updatedAt || "")));
}

export function buildDashboard(user) {
  const unreadMessages = store.messages.filter((item) => item.recipientUserId === user.id && !item.read).length;

  if (user.role === ROLES.STUDENT) {
    const student = getStudentByUserId(user.id);
    const guidance = getEffectiveGuidance(student.id) || getPendingGuidance(student.id);
    const internship = getCurrentInternship(student.id);
    const templates = getAllowedTemplates(student.internshipType);
    const archivedCount = store.formInstances.filter(
      (item) => item.studentId === student.id && item.status === FORM_STATUSES.ARCHIVED
    ).length;
    return {
      cards: [
        { label: "指导关系", value: guidance ? GUIDANCE_STATUS_LABELS[guidance.status] : "未建立" },
        { label: "实习申请", value: internship ? INTERNSHIP_APPLICATION_STATUS_LABELS[internship.status] : "未提交" },
        { label: "已归档表单", value: `${archivedCount}/${templates.length}` },
        { label: "未读消息", value: String(unreadMessages) }
      ]
    };
  }

  if (user.role === ROLES.TEACHER) {
    const teacher = getTeacherByUserId(user.id);
    const activeStudents = store.guidanceRelations.filter(
      (item) => item.teacherId === teacher.id && item.status === GUIDANCE_STATUSES.EFFECTIVE
    ).length;
    const pendingGuidance = store.guidanceRelations.filter(
      (item) => item.teacherId === teacher.id && item.status === GUIDANCE_STATUSES.PENDING_TEACHER
    ).length;
    const pendingForms = store.formInstances.filter(
      (item) => item.teacherId === teacher.id && [FORM_STATUSES.SUBMITTED, FORM_STATUSES.RESUBMITTED].includes(item.status)
    ).length;
    return {
      cards: [
        { label: "负责学生数", value: String(activeStudents) },
        { label: "待确认指导申请", value: String(pendingGuidance) },
        { label: "待审核表单", value: String(pendingForms) },
        { label: "未读消息", value: String(unreadMessages) }
      ]
    };
  }

  if (user.role === ROLES.COLLEGE_ADMIN) {
    const collegeId = user.collegeId;
    return {
      cards: [
        { label: "学生数", value: String(store.students.filter((item) => item.collegeId === collegeId).length) },
        { label: "教师数", value: String(store.teachers.filter((item) => item.collegeId === collegeId).length) },
        { label: "待复核指导关系", value: String(store.guidanceRelations.filter((item) => item.collegeId === collegeId && item.status === GUIDANCE_STATUSES.PENDING_COLLEGE).length) },
        { label: "待审批实习申请", value: String(store.internshipApplications.filter((item) => item.collegeId === collegeId && item.status === INTERNSHIP_APPLICATION_STATUSES.SUBMITTED).length) },
        { label: "待归档表单", value: String(store.formInstances.filter((item) => item.collegeId === collegeId && item.status === FORM_STATUSES.COLLEGE_REVIEWING).length) },
        { label: "未读消息", value: String(unreadMessages) }
      ]
    };
  }

  return {
    cards: [
      { label: "学院数", value: String(store.colleges.length) },
      { label: "待审核入驻申请", value: String(store.collegeApplications.filter((item) => item.status === "pending").length) },
      { label: "操作日志数", value: String(store.auditLogs.length) },
      { label: "未读消息", value: String(unreadMessages) }
    ]
  };
}

export function getOverviewReports(collegeId) {
  const students = store.students.filter((item) => item.collegeId === collegeId);
  const teachers = store.teachers.filter((item) => item.collegeId === collegeId);
  const units = store.internshipUnits.filter((item) => item.collegeId === collegeId);
  const forms = store.formInstances.filter((item) => item.collegeId === collegeId);
  const teacherWorkload = teachers.map((teacher) => ({
    teacherId: teacher.id,
    teacherName: teacher.name,
    assignedStudents: store.guidanceRelations.filter(
      (item) => item.teacherId === teacher.id && item.status === GUIDANCE_STATUSES.EFFECTIVE
    ).length,
    reviewedForms: forms.filter(
      (item) => item.teacherId === teacher.id && [FORM_STATUSES.COLLEGE_REVIEWING, FORM_STATUSES.ARCHIVED].includes(item.status)
    ).length
  }));
  const studentProgress = students.map((student) => {
    const totalTemplates = getAllowedTemplates(student.internshipType).length;
    const archivedForms = forms.filter((item) => item.studentId === student.id && item.status === FORM_STATUSES.ARCHIVED).length;
    return {
      studentId: student.id,
      studentName: student.name,
      studentNo: student.studentNo,
      internshipType: student.internshipType,
      archivedForms,
      completionRate: totalTemplates ? `${Math.round((archivedForms / totalTemplates) * 100)}%` : "0%"
    };
  });

  return {
    summary: {
      studentCount: students.length,
      teacherCount: teachers.length,
      unitCount: units.length,
      activeInternshipCount: store.internshipApplications.filter((item) => item.collegeId === collegeId && item.status === INTERNSHIP_APPLICATION_STATUSES.ACTIVE).length,
      archivedFormCount: forms.filter((item) => item.status === FORM_STATUSES.ARCHIVED).length,
      returnedFormCount: forms.filter((item) => [FORM_STATUSES.TEACHER_RETURNED, FORM_STATUSES.COLLEGE_RETURNED].includes(item.status)).length
    },
    teacherWorkload,
    studentProgress
  };
}
