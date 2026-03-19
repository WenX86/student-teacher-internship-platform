import { FORM_STATUSES, MENTOR_APPLICATION_STATUSES, ROLES } from "./constants.js";

function ratio(numerator, denominator) {
  if (!denominator) {
    return 0;
  }

  return Math.round((numerator / denominator) * 100);
}

export function getDashboard(db, user) {
  switch (user.role) {
    case ROLES.STUDENT:
      return getStudentDashboard(db, user);
    case ROLES.TEACHER:
      return getTeacherDashboard(db, user);
    case ROLES.COLLEGE_ADMIN:
      return getCollegeDashboard(db, user);
    case ROLES.SUPER_ADMIN:
      return getSuperDashboard(db);
    default:
      return {};
  }
}

function getStudentDashboard(db, user) {
  const student = db.students.find((item) => item.userId === user.id);
  const mentorApplication = db.mentorApplications.find((item) => item.studentId === student?.id);
  const internshipApplication = db.internshipApplications.find((item) => item.studentId === student?.id);
  const forms = db.formInstances.filter((item) => item.studentId === student?.id);
  const todoCount = forms.filter((item) =>
    [FORM_STATUSES.DRAFT, FORM_STATUSES.TEACHER_RETURNED, FORM_STATUSES.COLLEGE_RETURNED].includes(item.status)
  ).length;

  return {
    internshipStatus: student?.internshipStatus ?? "待完善",
    mentorStatus: mentorApplication?.status ?? "未申请",
    organizationStatus: internshipApplication?.status ?? "未申请",
    totalForms: forms.length,
    todoCount,
    returnedCount: forms.filter((item) => [FORM_STATUSES.TEACHER_RETURNED, FORM_STATUSES.COLLEGE_RETURNED].includes(item.status)).length,
    archivedCount: forms.filter((item) => item.status === FORM_STATUSES.ARCHIVED).length,
  };
}

function getTeacherDashboard(db, user) {
  const teacher = db.teachers.find((item) => item.userId === user.id);
  const activeStudentIds = db.mentorApplications
    .filter((item) => item.teacherId === teacher?.id && item.status === MENTOR_APPLICATION_STATUSES.EFFECTIVE)
    .map((item) => item.studentId);
  const pendingMentorRequests = db.mentorApplications.filter(
    (item) => item.teacherId === teacher?.id && item.status === MENTOR_APPLICATION_STATUSES.PENDING_TEACHER
  );
  const reviewForms = db.formInstances.filter((item) => activeStudentIds.includes(item.studentId));
  const pendingReviewCount = reviewForms.filter((item) => item.status === FORM_STATUSES.TEACHER_REVIEWING).length;
  const archivedCount = reviewForms.filter((item) => item.status === FORM_STATUSES.ARCHIVED).length;

  return {
    studentCount: activeStudentIds.length,
    pendingMentorRequests: pendingMentorRequests.length,
    pendingReviewCount,
    archivedCount,
    completionRate: ratio(archivedCount, reviewForms.length || 1),
  };
}

function getCollegeDashboard(db, user) {
  const collegeStudents = db.students.filter((item) => item.collegeId === user.collegeId);
  const collegeTeachers = db.teachers.filter((item) => item.collegeId === user.collegeId);
  const collegeForms = db.formInstances.filter((item) =>
    collegeStudents.some((student) => student.id === item.studentId)
  );

  return {
    studentCount: collegeStudents.length,
    teacherCount: collegeTeachers.length,
    pendingMentorReviewCount: db.mentorApplications.filter((item) => item.status === MENTOR_APPLICATION_STATUSES.PENDING_COLLEGE).length,
    pendingInternshipReviewCount: db.internshipApplications.filter((item) => item.status === "待学院审批").length,
    pendingArchiveCount: collegeForms.filter((item) => item.status === FORM_STATUSES.COLLEGE_REVIEWING).length,
    archivedCount: collegeForms.filter((item) => item.status === FORM_STATUSES.ARCHIVED).length,
    riskStudentCount: collegeForms.filter((item) => [FORM_STATUSES.TEACHER_RETURNED, FORM_STATUSES.COLLEGE_RETURNED].includes(item.status)).length,
  };
}

function getSuperDashboard(db) {
  return {
    activeUsers: db.users.filter((item) => item.status === "ACTIVE").length,
    collegeApplicationCount: db.collegeApplications.length,
    pendingCollegeApplicationCount: db.collegeApplications.filter((item) => item.status === "待审核").length,
    totalForms: db.formInstances.length,
    unreadMessages: db.messages.filter((item) => !item.read).length,
  };
}

export function getReportSummary(db) {
  const totalForms = db.formInstances.length;
  const archivedForms = db.formInstances.filter((item) => item.status === FORM_STATUSES.ARCHIVED).length;
  const rejectedForms = db.formInstances.filter((item) =>
    [FORM_STATUSES.TEACHER_RETURNED, FORM_STATUSES.COLLEGE_RETURNED].includes(item.status)
  ).length;

  return {
    students: {
      total: db.students.length,
      applied: db.internshipApplications.length,
      active: db.students.filter((item) => item.internshipStatus === "实习中").length,
    },
    teachers: {
      total: db.teachers.length,
      activeGuidanceCount: db.mentorApplications.filter((item) => item.status === MENTOR_APPLICATION_STATUSES.EFFECTIVE).length,
    },
    forms: {
      total: totalForms,
      archived: archivedForms,
      archiveRate: ratio(archivedForms, totalForms || 1),
      rejected: rejectedForms,
    },
  };
}
