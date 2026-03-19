import crypto from "node:crypto";
import {
  FORM_STATUSES,
  FORM_TEMPLATES,
  GUIDANCE_STATUSES,
  INTERNSHIP_APPLICATION_STATUSES,
  INTERNSHIP_TYPES,
  MESSAGE_TYPES,
  ROLES
} from "./backend-constants.js";

export function hashPassword(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function iso(value) {
  return new Date(value).toISOString();
}

function historyEntry(actor, action, comment, at) {
  return {
    actor,
    action,
    comment,
    at
  };
}

export function createInitialState() {
  const state = {
    users: [
      {
        id: "user-super-1",
        account: "superadmin",
        name: "平台管理员",
        role: ROLES.SUPER_ADMIN,
        collegeId: null,
        passwordHash: hashPassword("Admin@123"),
        mustChangePassword: false,
        lastLoginAt: null
      },
      {
        id: "user-admin-1",
        account: "edu-admin",
        name: "教育学院管理员",
        role: ROLES.COLLEGE_ADMIN,
        collegeId: "college-1",
        passwordHash: hashPassword("Admin@123"),
        mustChangePassword: false,
        lastLoginAt: null
      },
      {
        id: "user-teacher-1",
        account: "T2026001",
        name: "陈老师",
        role: ROLES.TEACHER,
        collegeId: "college-1",
        passwordHash: hashPassword("Teacher@123"),
        mustChangePassword: false,
        lastLoginAt: null
      },
      {
        id: "user-teacher-2",
        account: "T2026002",
        name: "周老师",
        role: ROLES.TEACHER,
        collegeId: "college-1",
        passwordHash: hashPassword("Teacher@123"),
        mustChangePassword: false,
        lastLoginAt: null
      },
      {
        id: "user-student-1",
        account: "20260001",
        name: "张琳",
        role: ROLES.STUDENT,
        collegeId: "college-1",
        passwordHash: hashPassword("Student@123"),
        mustChangePassword: false,
        lastLoginAt: null
      },
      {
        id: "user-student-2",
        account: "20260002",
        name: "李涛",
        role: ROLES.STUDENT,
        collegeId: "college-1",
        passwordHash: hashPassword("Student@123"),
        mustChangePassword: false,
        lastLoginAt: null
      }
    ],
    colleges: [
      {
        id: "college-1",
        schoolName: "示范大学",
        name: "教育学院",
        contactName: "李主任",
        contactPhone: "13800000001",
        status: "active"
      }
    ],
    collegeApplications: [
      {
        id: "college-application-1",
        schoolName: "省立师范大学",
        collegeName: "文学院",
        contactName: "王老师",
        contactPhone: "13810000001",
        remark: "申请加入 2026 年教育实习平台试点。",
        status: "pending",
        reviewedAt: null,
        reviewComment: ""
      }
    ],
    students: [
      {
        id: "student-1",
        userId: "user-student-1",
        collegeId: "college-1",
        studentNo: "20260001",
        name: "张琳",
        major: "汉语言文学",
        className: "2026 级 1 班",
        internshipType: INTERNSHIP_TYPES.TEACHING,
        internshipBatch: "2026 春季",
        phone: "13900000001",
        email: "zhanglin@example.com",
        profileComplete: true,
        status: "active"
      },
      {
        id: "student-2",
        userId: "user-student-2",
        collegeId: "college-1",
        studentNo: "20260002",
        name: "李涛",
        major: "思想政治教育",
        className: "2026 级 2 班",
        internshipType: INTERNSHIP_TYPES.HEAD_TEACHER,
        internshipBatch: "2026 春季",
        phone: "13900000002",
        email: "litao@example.com",
        profileComplete: true,
        status: "active"
      }
    ],
    teachers: [
      {
        id: "teacher-1",
        userId: "user-teacher-1",
        collegeId: "college-1",
        teacherNo: "T2026001",
        name: "陈老师",
        department: "语文教研室",
        phone: "13700000001",
        title: "副教授",
        status: "active"
      },
      {
        id: "teacher-2",
        userId: "user-teacher-2",
        collegeId: "college-1",
        teacherNo: "T2026002",
        name: "周老师",
        department: "德育教研室",
        phone: "13700000002",
        title: "讲师",
        status: "active"
      }
    ],
    internshipUnits: [
      {
        id: "unit-1",
        collegeId: "college-1",
        name: "示范中学",
        address: "市南区书香路 18 号",
        contactName: "刘校长",
        contactPhone: "13600000001",
        unitType: "公办中学",
        cooperationStatus: "优质合作",
        yearsAccepted: 16,
        evaluation: "长期稳定合作。"
      },
      {
        id: "unit-2",
        collegeId: "college-1",
        name: "实验小学",
        address: "市北区育才街 88 号",
        contactName: "王主任",
        contactPhone: "13600000002",
        unitType: "公办小学",
        cooperationStatus: "正常合作",
        yearsAccepted: 11,
        evaluation: "接收流程规范。"
      }
    ],
    guidanceRelations: [
      {
        id: "guidance-1",
        studentId: "student-1",
        teacherId: "teacher-1",
        collegeId: "college-1",
        status: GUIDANCE_STATUSES.EFFECTIVE,
        requestedAt: iso("2026-02-10T09:00:00"),
        teacherActionAt: iso("2026-02-10T12:30:00"),
        collegeActionAt: iso("2026-02-11T10:20:00"),
        rejectionReason: "",
        history: [
          historyEntry("student", "submitted", "学生提交指导教师申请。", iso("2026-02-10T09:00:00")),
          historyEntry("teacher", "approved", "教师确认接收。", iso("2026-02-10T12:30:00")),
          historyEntry("college_admin", "approved", "学院复核通过。", iso("2026-02-11T10:20:00"))
        ]
      }
    ],
    internshipApplications: [
      {
        id: "internship-1",
        studentId: "student-1",
        collegeId: "college-1",
        unitId: "unit-1",
        status: INTERNSHIP_APPLICATION_STATUSES.ACTIVE,
        term: "2026 春季",
        plannedStartDate: "2026-03-01",
        plannedEndDate: "2026-05-30",
        requestedNewUnit: false,
        newUnitName: "",
        newUnitAddress: "",
        materials: "接收函、健康承诺书",
        note: "按照学院安排进入语文学科任课实习。",
        externalConfirmationStatus: "confirmed",
        externalFeedback: "单位已确认接收。",
        receivedAt: "2026-02-26",
        reviewedAt: iso("2026-02-25T15:00:00"),
        reviewComment: "审批通过。"
      }
    ],
    formInstances: [
      {
        id: "form-1",
        templateCode: "practice-log",
        studentId: "student-1",
        teacherId: "teacher-1",
        collegeId: "college-1",
        internshipType: INTERNSHIP_TYPES.TEACHING,
        title: "第一周实习记录",
        content: "完成听课、备课与课堂观察。",
        attachments: ["week1-notes.docx"],
        status: FORM_STATUSES.ARCHIVED,
        version: 1,
        score: 92,
        reviewTrail: [
          historyEntry("student", "submitted", "学生提交。", iso("2026-03-03T08:10:00")),
          historyEntry("teacher", "approved", "教师审核通过，建议增加课堂反思。", iso("2026-03-03T13:20:00")),
          historyEntry("college_admin", "archived", "学院归档完成。", iso("2026-03-04T09:00:00"))
        ],
        createdAt: iso("2026-03-02T20:00:00"),
        updatedAt: iso("2026-03-04T09:00:00")
      },
      {
        id: "form-2",
        templateCode: "lecture-notes",
        studentId: "student-1",
        teacherId: "teacher-1",
        collegeId: "college-1",
        internshipType: INTERNSHIP_TYPES.TEACHING,
        title: "语文组听课记录",
        content: "完成两节示范课听课记录，待教师审核。",
        attachments: ["lecture-summary.pdf"],
        status: FORM_STATUSES.SUBMITTED,
        version: 1,
        score: null,
        reviewTrail: [
          historyEntry("student", "submitted", "学生提交。", iso("2026-03-08T11:00:00"))
        ],
        createdAt: iso("2026-03-08T10:50:00"),
        updatedAt: iso("2026-03-08T11:00:00")
      },
      {
        id: "form-3",
        templateCode: "duty-log",
        studentId: "student-2",
        teacherId: null,
        collegeId: "college-1",
        internshipType: INTERNSHIP_TYPES.HEAD_TEACHER,
        title: "班主任值守记录",
        content: "尚未提交，保留草稿。",
        attachments: [],
        status: FORM_STATUSES.DRAFT,
        version: 1,
        score: null,
        reviewTrail: [],
        createdAt: iso("2026-03-07T20:00:00"),
        updatedAt: iso("2026-03-07T20:00:00")
      }
    ],
    guidanceRecords: [
      {
        id: "record-1",
        studentId: "student-1",
        teacherId: "teacher-1",
        content: "围绕第一次试讲进行了教案点评。",
        issue: "课堂过渡不够自然。",
        suggestion: "增加板书设计和提问衔接。",
        followUp: "下周提交修改稿。",
        createdAt: iso("2026-03-05T19:00:00")
      }
    ],
    messages: [
      {
        id: "message-1",
        recipientUserId: "user-student-1",
        type: MESSAGE_TYPES.REVIEW_RESULT,
        title: "实习记录已归档",
        content: "第一周实习记录已通过学院终审并归档。",
        relatedType: "form",
        relatedId: "form-1",
        link: "#student/forms",
        read: false,
        createdAt: iso("2026-03-04T09:05:00")
      },
      {
        id: "message-2",
        recipientUserId: "user-teacher-1",
        type: MESSAGE_TYPES.TODO,
        title: "有新的听课记录待审核",
        content: "张琳提交了语文组听课记录，请及时审核。",
        relatedType: "form",
        relatedId: "form-2",
        link: "#teacher/forms",
        read: false,
        createdAt: iso("2026-03-08T11:01:00")
      },
      {
        id: "message-3",
        recipientUserId: "user-admin-1",
        type: MESSAGE_TYPES.ANNOUNCEMENT,
        title: "一期原型已初始化",
        content: "请从指导关系、实习申请和表单归档流程开始验收。",
        relatedType: "system",
        relatedId: "init",
        link: "#admin/dashboard",
        read: false,
        createdAt: iso("2026-03-01T09:00:00")
      }
    ],
    auditLogs: [
      {
        id: "audit-1",
        actorUserId: "user-admin-1",
        action: "INIT_PLATFORM",
        entityType: "system",
        entityId: "phase1",
        detail: "初始化一期原型数据。",
        createdAt: iso("2026-03-01T08:30:00")
      }
    ],
    sessions: []
  };

  state.formTemplates = FORM_TEMPLATES;
  return state;
}

export const store = createInitialState();
