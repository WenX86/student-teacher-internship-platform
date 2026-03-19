import {
  FORM_STATUSES,
  FORM_TEMPLATES,
  GUIDANCE_STATUSES,
  INTERNSHIP_APPLICATION_STATUSES,
  INTERNSHIP_TYPES,
  MESSAGE_TYPES,
  ROLES
} from "./backend-constants.js";
import { hashPassword, store } from "./backend-store.js";
import {
  addAudit,
  addMessage,
  createId,
  getAllowedTemplates,
  getCurrentInternship,
  getEffectiveGuidance,
  getPendingGuidance,
  getStudentById,
  getStudentByUserId,
  getTeacherById,
  getTeacherByUserId,
  getTeacherUserId,
  nowIso,
  serializeForm,
  serializeGuidance,
  serializeInternship
} from "./backend-helpers.js";

function assertCollegeScope(user, collegeId) {
  return user.role === ROLES.SUPER_ADMIN || user.collegeId === collegeId;
}

export function createGuidanceRequest(user, body) {
  const student = getStudentByUserId(user.id);
  const teacher = getTeacherById(String(body.teacherId || ""));
  if (!teacher || teacher.collegeId !== student.collegeId) {
    throw new Error("指导教师不存在。");
  }
  if (getEffectiveGuidance(student.id)) {
    throw new Error("当前学生已存在生效中的指导关系。");
  }
  if (getPendingGuidance(student.id)) {
    throw new Error("当前学生已有待处理指导申请。");
  }

  const entity = {
    id: createId("guidance"),
    studentId: student.id,
    teacherId: teacher.id,
    collegeId: student.collegeId,
    status: GUIDANCE_STATUSES.PENDING_TEACHER,
    requestedAt: nowIso(),
    teacherActionAt: null,
    collegeActionAt: null,
    rejectionReason: "",
    history: [
      {
        actor: "student",
        action: "submitted",
        comment: body.note || "学生提交指导教师申请。",
        at: nowIso()
      }
    ]
  };

  store.guidanceRelations.unshift(entity);
  addAudit(user.id, "CREATE_GUIDANCE_REQUEST", "guidance", entity.id, `学生 ${student.name} 向教师 ${teacher.name} 发起指导申请。`);
  addMessage(teacher.userId, MESSAGE_TYPES.TODO, "有新的指导教师申请待确认", `${student.name} 提交了指导教师申请。`, "guidance", entity.id, "#teacher/guidance");
  return serializeGuidance(entity);
}

export function reviewGuidanceByTeacher(user, relationId, body) {
  const teacher = getTeacherByUserId(user.id);
  const relation = store.guidanceRelations.find((item) => item.id === relationId);
  if (!relation || relation.teacherId !== teacher.id) {
    throw new Error("指导申请不存在。");
  }
  if (relation.status !== GUIDANCE_STATUSES.PENDING_TEACHER) {
    throw new Error("当前指导申请不在教师处理阶段。");
  }

  relation.teacherActionAt = nowIso();
  if (body.action === "approve") {
    relation.status = GUIDANCE_STATUSES.PENDING_COLLEGE;
    relation.history.unshift({
      actor: "teacher",
      action: "approved",
      comment: body.comment || "教师确认接收。",
      at: relation.teacherActionAt
    });
    addMessage(
      store.users.find((entry) => entry.role === ROLES.COLLEGE_ADMIN && entry.collegeId === relation.collegeId)?.id,
      MESSAGE_TYPES.TODO,
      "有待复核的指导关系",
      "教师已确认学生指导关系，请学院复核。",
      "guidance",
      relation.id,
      "#admin/guidance"
    );
  } else {
    relation.status = GUIDANCE_STATUSES.TEACHER_REJECTED;
    relation.rejectionReason = body.comment || "教师驳回指导申请。";
    relation.history.unshift({
      actor: "teacher",
      action: "rejected",
      comment: relation.rejectionReason,
      at: relation.teacherActionAt
    });
    addMessage(getStudentById(relation.studentId).userId, MESSAGE_TYPES.RETURN_NOTICE, "指导申请被教师驳回", relation.rejectionReason, "guidance", relation.id, "#student/guidance");
  }

  addAudit(user.id, "TEACHER_REVIEW_GUIDANCE", "guidance", relation.id, `教师处理指导申请：${body.action}`);
  return serializeGuidance(relation);
}

export function reviewGuidanceByAdmin(user, relationId, body) {
  const relation = store.guidanceRelations.find((item) => item.id === relationId);
  if (!relation || !assertCollegeScope(user, relation.collegeId)) {
    throw new Error("指导申请不存在。");
  }
  if (relation.status !== GUIDANCE_STATUSES.PENDING_COLLEGE) {
    throw new Error("当前指导申请不在学院复核阶段。");
  }

  relation.collegeActionAt = nowIso();
  const student = getStudentById(relation.studentId);
  if (body.action === "approve") {
    relation.status = GUIDANCE_STATUSES.EFFECTIVE;
    relation.history.unshift({
      actor: "college_admin",
      action: "approved",
      comment: body.comment || "学院复核通过。",
      at: relation.collegeActionAt
    });
    addMessage(student.userId, MESSAGE_TYPES.REVIEW_RESULT, "指导关系已生效", "学院已复核通过，指导关系正式生效。", "guidance", relation.id, "#student/guidance");
  } else {
    relation.status = GUIDANCE_STATUSES.COLLEGE_REJECTED;
    relation.rejectionReason = body.comment || "学院驳回指导申请。";
    relation.history.unshift({
      actor: "college_admin",
      action: "rejected",
      comment: relation.rejectionReason,
      at: relation.collegeActionAt
    });
    addMessage(student.userId, MESSAGE_TYPES.RETURN_NOTICE, "指导申请被学院驳回", relation.rejectionReason, "guidance", relation.id, "#student/guidance");
  }

  addAudit(user.id, "ADMIN_REVIEW_GUIDANCE", "guidance", relation.id, `学院处理指导申请：${body.action}`);
  return serializeGuidance(relation);
}

export function createInternshipApplication(user, body) {
  const student = getStudentByUserId(user.id);
  if (!getEffectiveGuidance(student.id)) {
    throw new Error("请先建立生效中的指导关系。");
  }
  if (getCurrentInternship(student.id)) {
    throw new Error("当前学生已有进行中的实习申请。");
  }

  const requestedNewUnit = Boolean(body.requestedNewUnit);
  const unitId = requestedNewUnit ? null : String(body.unitId || "");
  if (!requestedNewUnit && !store.internshipUnits.some((item) => item.id === unitId)) {
    throw new Error("请选择有效的实习单位。");
  }

  const entity = {
    id: createId("internship"),
    studentId: student.id,
    collegeId: student.collegeId,
    unitId,
    status: INTERNSHIP_APPLICATION_STATUSES.SUBMITTED,
    term: body.term || student.internshipBatch,
    plannedStartDate: body.plannedStartDate || "",
    plannedEndDate: body.plannedEndDate || "",
    requestedNewUnit,
    newUnitName: requestedNewUnit ? String(body.newUnitName || "") : "",
    newUnitAddress: requestedNewUnit ? String(body.newUnitAddress || "") : "",
    materials: String(body.materials || ""),
    note: String(body.note || ""),
    externalConfirmationStatus: "pending",
    externalFeedback: "",
    receivedAt: "",
    reviewedAt: null,
    reviewComment: ""
  };

  if (requestedNewUnit && !entity.newUnitName) {
    throw new Error("新增单位备案时必须填写单位名称。");
  }

  store.internshipApplications.unshift(entity);
  addMessage(
    store.users.find((item) => item.role === ROLES.COLLEGE_ADMIN && item.collegeId === student.collegeId)?.id,
    MESSAGE_TYPES.TODO,
    "有新的实习申请待审批",
    `${student.name} 提交了实习申请。`,
    "internship",
    entity.id,
    "#admin/internship"
  );
  addAudit(user.id, "CREATE_INTERNSHIP_APPLICATION", "internship", entity.id, `学生 ${student.name} 提交实习申请。`);
  return serializeInternship(entity);
}

export function reviewInternshipByAdmin(user, internshipId, body) {
  const entity = store.internshipApplications.find((item) => item.id === internshipId);
  if (!entity || !assertCollegeScope(user, entity.collegeId)) {
    throw new Error("实习申请不存在。");
  }
  if (entity.status !== INTERNSHIP_APPLICATION_STATUSES.SUBMITTED) {
    throw new Error("当前实习申请不在审批阶段。");
  }

  entity.reviewedAt = nowIso();
  entity.reviewComment = body.comment || "";
  const student = getStudentById(entity.studentId);

  if (body.action === "approve") {
    if (entity.requestedNewUnit) {
      const newUnit = {
        id: createId("unit"),
        collegeId: entity.collegeId,
        name: entity.newUnitName,
        address: entity.newUnitAddress || "",
        contactName: String(body.contactName || "待补充"),
        contactPhone: String(body.contactPhone || ""),
        unitType: String(body.unitType || "待补充"),
        cooperationStatus: "待观察",
        yearsAccepted: 1,
        evaluation: "由实习申请审批创建。"
      };
      store.internshipUnits.unshift(newUnit);
      entity.unitId = newUnit.id;
    }
    entity.externalConfirmationStatus = body.externalConfirmationStatus || "pending";
    entity.externalFeedback = body.externalFeedback || "";
    entity.receivedAt = body.receivedAt || "";
    entity.status =
      entity.externalConfirmationStatus === "confirmed"
        ? INTERNSHIP_APPLICATION_STATUSES.ACTIVE
        : INTERNSHIP_APPLICATION_STATUSES.APPROVED_PENDING_EXTERNAL;
    addMessage(student.userId, MESSAGE_TYPES.REVIEW_RESULT, "实习申请已通过", entity.status === INTERNSHIP_APPLICATION_STATUSES.ACTIVE ? "学院已审批通过，学生已进入实习阶段。" : "学院已审批通过，待补录单位外部确认结果。", "internship", entity.id, "#student/internship");
  } else {
    entity.status = INTERNSHIP_APPLICATION_STATUSES.REJECTED;
    addMessage(student.userId, MESSAGE_TYPES.RETURN_NOTICE, "实习申请被退回", entity.reviewComment || "学院退回实习申请。", "internship", entity.id, "#student/internship");
  }

  addAudit(user.id, "ADMIN_REVIEW_INTERNSHIP", "internship", entity.id, `学院处理实习申请：${body.action}`);
  return serializeInternship(entity);
}

export function createOrUpdateForm(user, body) {
  const student = getStudentByUserId(user.id);
  const guidance = getEffectiveGuidance(student.id);
  const internship = getCurrentInternship(student.id);
  if (!guidance || !internship) {
    throw new Error("请先确保指导关系已生效且实习申请已进入执行阶段。");
  }

  const template = FORM_TEMPLATES.find((item) => item.code === body.templateCode);
  if (!template) {
    throw new Error("表单模板不存在。");
  }
  if (![INTERNSHIP_TYPES.ALL, student.internshipType].includes(template.internshipType)) {
    throw new Error("当前学生不能提交该类型表单。");
  }

  const attachments = Array.isArray(body.attachments)
    ? body.attachments.map((item) => String(item).trim()).filter(Boolean)
    : [];
  let entity = body.formId ? store.formInstances.find((item) => item.id === body.formId && item.studentId === student.id) : null;

  if (entity && entity.status === FORM_STATUSES.ARCHIVED) {
    throw new Error("已归档表单不能直接编辑，请先提交修改申请。");
  }

  if (!entity) {
    entity = {
      id: createId("form"),
      templateCode: template.code,
      studentId: student.id,
      teacherId: guidance.teacherId,
      collegeId: student.collegeId,
      internshipType: student.internshipType,
      title: String(body.title || template.name),
      content: String(body.content || ""),
      attachments,
      status: FORM_STATUSES.DRAFT,
      version: 1,
      score: null,
      reviewTrail: [],
      createdAt: nowIso(),
      updatedAt: nowIso()
    };
    store.formInstances.unshift(entity);
  } else {
    entity.title = String(body.title || entity.title);
    entity.content = String(body.content || entity.content);
    entity.attachments = attachments;
    entity.updatedAt = nowIso();
  }

  if (body.action === "save") {
    if (![FORM_STATUSES.DRAFT, FORM_STATUSES.TEACHER_RETURNED, FORM_STATUSES.COLLEGE_RETURNED, FORM_STATUSES.EDITABLE].includes(entity.status)) {
      throw new Error("当前状态不允许保存草稿。");
    }
    entity.status = entity.status === FORM_STATUSES.EDITABLE ? FORM_STATUSES.EDITABLE : FORM_STATUSES.DRAFT;
    addAudit(user.id, "SAVE_FORM", "form", entity.id, `学生保存表单草稿：${entity.title}`);
    return serializeForm(entity);
  }

  if (![FORM_STATUSES.DRAFT, FORM_STATUSES.TEACHER_RETURNED, FORM_STATUSES.COLLEGE_RETURNED, FORM_STATUSES.EDITABLE].includes(entity.status)) {
    throw new Error("当前状态不允许提交。");
  }

  if ([FORM_STATUSES.TEACHER_RETURNED, FORM_STATUSES.COLLEGE_RETURNED, FORM_STATUSES.EDITABLE].includes(entity.status)) {
    entity.version += 1;
    entity.status = FORM_STATUSES.RESUBMITTED;
    entity.reviewTrail.unshift({
      actor: "student",
      action: "resubmitted",
      comment: "学生根据退回意见重新提交。",
      at: nowIso()
    });
  } else {
    entity.status = FORM_STATUSES.SUBMITTED;
    entity.reviewTrail.unshift({
      actor: "student",
      action: "submitted",
      comment: "学生提交表单。",
      at: nowIso()
    });
  }

  addMessage(getTeacherUserId(guidance.teacherId), MESSAGE_TYPES.TODO, "有新的表单待审核", `${student.name} 提交了 ${template.name}。`, "form", entity.id, "#teacher/forms");
  addAudit(user.id, "SUBMIT_FORM", "form", entity.id, `学生提交表单：${entity.title}`);
  return serializeForm(entity);
}

export function requestFormChange(user, formId, body) {
  const student = getStudentByUserId(user.id);
  const entity = store.formInstances.find((item) => item.id === formId && item.studentId === student.id);
  if (!entity) {
    throw new Error("表单不存在。");
  }
  if (entity.status !== FORM_STATUSES.ARCHIVED) {
    throw new Error("仅已归档表单可发起修改申请。");
  }

  entity.status = FORM_STATUSES.CHANGE_REQUESTED;
  entity.updatedAt = nowIso();
  entity.reviewTrail.unshift({
    actor: "student",
    action: "change_requested",
    comment: body.reason || "学生申请修改已归档内容。",
    at: nowIso()
  });

  addMessage(
    store.users.find((item) => item.role === ROLES.COLLEGE_ADMIN && item.collegeId === entity.collegeId)?.id,
    MESSAGE_TYPES.TODO,
    "有新的归档修改申请",
    `${student.name} 申请修改已归档表单。`,
    "form",
    entity.id,
    "#admin/forms"
  );
  addAudit(user.id, "REQUEST_FORM_CHANGE", "form", entity.id, `学生发起表单修改申请：${entity.title}`);
  return serializeForm(entity);
}

export function reviewFormByTeacher(user, formId, body) {
  const teacher = getTeacherByUserId(user.id);
  const entity = store.formInstances.find((item) => item.id === formId);
  if (!entity || entity.teacherId !== teacher.id) {
    throw new Error("表单不存在。");
  }
  if (![FORM_STATUSES.SUBMITTED, FORM_STATUSES.RESUBMITTED].includes(entity.status)) {
    throw new Error("当前表单不在教师审核阶段。");
  }

  const student = getStudentById(entity.studentId);
  entity.updatedAt = nowIso();
  if (body.action === "approve") {
    entity.status = FORM_STATUSES.COLLEGE_REVIEWING;
    entity.score = body.score ?? entity.score;
    entity.reviewTrail.unshift({
      actor: "teacher",
      action: "approved",
      comment: body.comment || "教师审核通过。",
      score: body.score ?? null,
      at: nowIso()
    });
    addMessage(
      store.users.find((item) => item.role === ROLES.COLLEGE_ADMIN && item.collegeId === entity.collegeId)?.id,
      MESSAGE_TYPES.TODO,
      "有新的表单待归档",
      `${student.name} 的 ${serializeForm(entity).templateName} 已通过教师审核。`,
      "form",
      entity.id,
      "#admin/forms"
    );
  } else {
    entity.status = FORM_STATUSES.TEACHER_RETURNED;
    entity.reviewTrail.unshift({
      actor: "teacher",
      action: "rejected",
      comment: body.comment || "教师退回修改。",
      score: body.score ?? null,
      at: nowIso()
    });
    addMessage(student.userId, MESSAGE_TYPES.RETURN_NOTICE, "表单被教师退回", body.comment || "请根据教师意见修改后重新提交。", "form", entity.id, "#student/forms");
  }

  addAudit(user.id, "TEACHER_REVIEW_FORM", "form", entity.id, `教师处理表单：${body.action}`);
  return serializeForm(entity);
}

export function reviewFormByAdmin(user, formId, body) {
  const entity = store.formInstances.find((item) => item.id === formId);
  if (!entity || !assertCollegeScope(user, entity.collegeId)) {
    throw new Error("表单不存在。");
  }
  const student = getStudentById(entity.studentId);

  if (body.action === "allow-edit") {
    if (entity.status !== FORM_STATUSES.CHANGE_REQUESTED) {
      throw new Error("当前表单不在修改申请阶段。");
    }
    entity.status = FORM_STATUSES.EDITABLE;
    entity.reviewTrail.unshift({
      actor: "college_admin",
      action: "allow_edit",
      comment: body.comment || "学院同意修改。",
      at: nowIso()
    });
    addMessage(student.userId, MESSAGE_TYPES.REVIEW_RESULT, "已允许修改归档表单", "学院已同意修改申请，请重新编辑并提交。", "form", entity.id, "#student/forms");
  } else {
    if (entity.status !== FORM_STATUSES.COLLEGE_REVIEWING) {
      throw new Error("当前表单不在学院审核阶段。");
    }
    if (body.action === "archive") {
      entity.status = FORM_STATUSES.ARCHIVED;
      entity.reviewTrail.unshift({
        actor: "college_admin",
        action: "archived",
        comment: body.comment || "学院终审归档。",
        at: nowIso()
      });
      addMessage(student.userId, MESSAGE_TYPES.REVIEW_RESULT, "表单已归档", "学院终审通过，表单已归档。", "form", entity.id, "#student/forms");
    } else {
      entity.status = FORM_STATUSES.COLLEGE_RETURNED;
      entity.reviewTrail.unshift({
        actor: "college_admin",
        action: "rejected",
        comment: body.comment || "学院退回修改。",
        at: nowIso()
      });
      addMessage(student.userId, MESSAGE_TYPES.RETURN_NOTICE, "表单被学院退回", body.comment || "请根据学院意见修改后重新提交。", "form", entity.id, "#student/forms");
    }
  }

  entity.updatedAt = nowIso();
  addAudit(user.id, "ADMIN_REVIEW_FORM", "form", entity.id, `学院处理表单：${body.action}`);
  return serializeForm(entity);
}

export function createGuidanceRecord(user, body) {
  const teacher = getTeacherByUserId(user.id);
  const student = getStudentById(String(body.studentId || ""));
  if (!student) {
    throw new Error("学生不存在。");
  }
  const effective = store.guidanceRelations.find(
    (item) => item.teacherId === teacher.id && item.studentId === student.id && item.status === GUIDANCE_STATUSES.EFFECTIVE
  );
  if (!effective) {
    throw new Error("仅可为已生效指导关系的学生记录指导内容。");
  }

  const entity = {
    id: createId("record"),
    studentId: student.id,
    teacherId: teacher.id,
    content: String(body.content || ""),
    issue: String(body.issue || ""),
    suggestion: String(body.suggestion || ""),
    followUp: String(body.followUp || ""),
    createdAt: nowIso()
  };
  if (!entity.content) {
    throw new Error("指导内容不能为空。");
  }

  store.guidanceRecords.unshift(entity);
  addAudit(user.id, "CREATE_GUIDANCE_RECORD", "guidance_record", entity.id, `教师为学生 ${student.name} 新增指导记录。`);
  addMessage(student.userId, MESSAGE_TYPES.REMINDER, "收到新的指导记录", "指导教师新增了过程指导记录，请及时查看。", "guidance_record", entity.id, "#student/messages");
  return entity;
}

export function createStudentByAdmin(user, body) {
  if (store.students.some((item) => item.studentNo === body.studentNo)) {
    throw new Error("学号已存在。");
  }
  if (store.users.some((item) => item.account === body.studentNo)) {
    throw new Error("账号已存在。");
  }

  const userEntity = {
    id: createId("user"),
    account: body.studentNo,
    name: body.name,
    role: ROLES.STUDENT,
    collegeId: user.collegeId,
    passwordHash: hashPassword("Student@123"),
    mustChangePassword: true,
    lastLoginAt: null
  };
  const studentEntity = {
    id: createId("student"),
    userId: userEntity.id,
    collegeId: user.collegeId,
    studentNo: body.studentNo,
    name: body.name,
    major: body.major || "",
    className: body.className || "",
    internshipType: body.internshipType || INTERNSHIP_TYPES.TEACHING,
    internshipBatch: body.internshipBatch || "2026 春季",
    phone: body.phone || "",
    email: body.email || "",
    profileComplete: true,
    status: body.status || "active"
  };
  store.users.unshift(userEntity);
  store.students.unshift(studentEntity);
  addAudit(user.id, "CREATE_STUDENT", "student", studentEntity.id, `学院新增学生 ${studentEntity.name}。`);
  return studentEntity;
}

export function createTeacherByAdmin(user, body) {
  if (store.teachers.some((item) => item.teacherNo === body.teacherNo)) {
    throw new Error("工号已存在。");
  }
  if (store.users.some((item) => item.account === body.teacherNo)) {
    throw new Error("账号已存在。");
  }

  const userEntity = {
    id: createId("user"),
    account: body.teacherNo,
    name: body.name,
    role: ROLES.TEACHER,
    collegeId: user.collegeId,
    passwordHash: hashPassword("Teacher@123"),
    mustChangePassword: true,
    lastLoginAt: null
  };
  const teacherEntity = {
    id: createId("teacher"),
    userId: userEntity.id,
    collegeId: user.collegeId,
    teacherNo: body.teacherNo,
    name: body.name,
    department: body.department || "",
    phone: body.phone || "",
    title: body.title || "",
    status: body.status || "active"
  };
  store.users.unshift(userEntity);
  store.teachers.unshift(teacherEntity);
  addAudit(user.id, "CREATE_TEACHER", "teacher", teacherEntity.id, `学院新增教师 ${teacherEntity.name}。`);
  return teacherEntity;
}

export function createUnitByAdmin(user, body) {
  const entity = {
    id: createId("unit"),
    collegeId: user.collegeId,
    name: String(body.name || ""),
    address: String(body.address || ""),
    contactName: String(body.contactName || ""),
    contactPhone: String(body.contactPhone || ""),
    unitType: String(body.unitType || ""),
    cooperationStatus: String(body.cooperationStatus || "正常合作"),
    yearsAccepted: Number(body.yearsAccepted || 1),
    evaluation: String(body.evaluation || "")
  };
  if (!entity.name) {
    throw new Error("单位名称不能为空。");
  }
  store.internshipUnits.unshift(entity);
  addAudit(user.id, "CREATE_UNIT", "unit", entity.id, `学院新增实习单位 ${entity.name}。`);
  return entity;
}

export function updateEntity(target, payload, keys) {
  keys.forEach((key) => {
    if (key in payload) {
      target[key] = payload[key];
    }
  });
  return target;
}

export function getStudentTemplates(user) {
  const student = getStudentByUserId(user.id);
  return {
    templates: getAllowedTemplates(student.internshipType),
    forms: store.formInstances.filter((item) => item.studentId === student.id).map(serializeForm)
  };
}
