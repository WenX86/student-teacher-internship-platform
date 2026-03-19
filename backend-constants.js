export const ROLES = {
  STUDENT: "STUDENT",
  TEACHER: "TEACHER",
  COLLEGE_ADMIN: "COLLEGE_ADMIN",
  SUPER_ADMIN: "SUPER_ADMIN",
};

export const INTERNSHIP_TYPES = {
  ALL: "ALL",
  TEACHING: "TEACHING",
  HEAD_TEACHER: "HEAD_TEACHER",
};

export const GUIDANCE_STATUSES = {
  PENDING_TEACHER: "pending_teacher",
  TEACHER_REJECTED: "teacher_rejected",
  PENDING_COLLEGE: "pending_college",
  EFFECTIVE: "effective",
  COLLEGE_REJECTED: "college_rejected",
};

export const GUIDANCE_STATUS_LABELS = {
  [GUIDANCE_STATUSES.PENDING_TEACHER]: "待教师确认",
  [GUIDANCE_STATUSES.TEACHER_REJECTED]: "教师驳回",
  [GUIDANCE_STATUSES.PENDING_COLLEGE]: "待学院复核",
  [GUIDANCE_STATUSES.EFFECTIVE]: "已生效",
  [GUIDANCE_STATUSES.COLLEGE_REJECTED]: "学院驳回",
};

export const INTERNSHIP_APPLICATION_STATUSES = {
  SUBMITTED: "submitted",
  REJECTED: "rejected",
  APPROVED_PENDING_EXTERNAL: "approved_pending_external",
  ACTIVE: "active",
};

export const INTERNSHIP_APPLICATION_STATUS_LABELS = {
  [INTERNSHIP_APPLICATION_STATUSES.SUBMITTED]: "待学院审批",
  [INTERNSHIP_APPLICATION_STATUSES.REJECTED]: "已退回",
  [INTERNSHIP_APPLICATION_STATUSES.APPROVED_PENDING_EXTERNAL]: "待单位外部确认",
  [INTERNSHIP_APPLICATION_STATUSES.ACTIVE]: "已进入实习阶段",
};

export const FORM_STATUSES = {
  DRAFT: "draft",
  SUBMITTED: "submitted",
  RESUBMITTED: "resubmitted",
  TEACHER_RETURNED: "teacher_returned",
  COLLEGE_REVIEWING: "college_reviewing",
  COLLEGE_RETURNED: "college_returned",
  ARCHIVED: "archived",
  CHANGE_REQUESTED: "change_requested",
  EDITABLE: "editable",
};

export const FORM_STATUS_LABELS = {
  [FORM_STATUSES.DRAFT]: "草稿",
  [FORM_STATUSES.SUBMITTED]: "已提交",
  [FORM_STATUSES.RESUBMITTED]: "已重新提交",
  [FORM_STATUSES.TEACHER_RETURNED]: "教师退回",
  [FORM_STATUSES.COLLEGE_REVIEWING]: "学院审核中",
  [FORM_STATUSES.COLLEGE_RETURNED]: "学院退回",
  [FORM_STATUSES.ARCHIVED]: "已归档",
  [FORM_STATUSES.CHANGE_REQUESTED]: "修改申请中",
  [FORM_STATUSES.EDITABLE]: "允许修改",
};

export const MESSAGE_TYPES = {
  TODO: "todo",
  REVIEW_RESULT: "review_result",
  RETURN_NOTICE: "return_notice",
  REMINDER: "reminder",
  ANNOUNCEMENT: "announcement",
};

export const MESSAGE_TYPE_LABELS = {
  [MESSAGE_TYPES.TODO]: "待办提醒",
  [MESSAGE_TYPES.REVIEW_RESULT]: "审核结果",
  [MESSAGE_TYPES.RETURN_NOTICE]: "退回通知",
  [MESSAGE_TYPES.REMINDER]: "催办提醒",
  [MESSAGE_TYPES.ANNOUNCEMENT]: "系统公告",
};

export const FORM_TEMPLATES = [
  { code: "practice-log", name: "实习记录", category: "通用表单", internshipType: INTERNSHIP_TYPES.ALL },
  { code: "practice-insight", name: "实习心得", category: "通用表单", internshipType: INTERNSHIP_TYPES.ALL },
  { code: "practice-summary", name: "实习总结", category: "通用表单", internshipType: INTERNSHIP_TYPES.ALL },
  { code: "self-evaluation", name: "自评表", category: "通用表单", internshipType: INTERNSHIP_TYPES.ALL },
  { code: "lecture-notes", name: "听课记录", category: "任课实习", internshipType: INTERNSHIP_TYPES.TEACHING },
  { code: "trial-teaching", name: "试讲申请", category: "任课实习", internshipType: INTERNSHIP_TYPES.TEACHING },
  { code: "lesson-material", name: "课程材料", category: "任课实习", internshipType: INTERNSHIP_TYPES.TEACHING },
  { code: "research-report", name: "教育研习", category: "任课实习", internshipType: INTERNSHIP_TYPES.TEACHING },
  { code: "teaching-checkin", name: "教学打卡", category: "任课实习", internshipType: INTERNSHIP_TYPES.TEACHING },
  { code: "duty-log", name: "值守记录", category: "班主任实习", internshipType: INTERNSHIP_TYPES.HEAD_TEACHER },
  { code: "class-meeting", name: "班会方案与总结", category: "班主任实习", internshipType: INTERNSHIP_TYPES.HEAD_TEACHER },
  { code: "headteacher-work", name: "班主任工作记录", category: "班主任实习", internshipType: INTERNSHIP_TYPES.HEAD_TEACHER },
  { code: "headteacher-checkin", name: "班主任实习打卡", category: "班主任实习", internshipType: INTERNSHIP_TYPES.HEAD_TEACHER },
  { code: "public-course", name: "公开课材料", category: "班主任实习", internshipType: INTERNSHIP_TYPES.HEAD_TEACHER }
];
