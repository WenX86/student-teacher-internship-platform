<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { ElMessageBox } from "element-plus";
import { downloadFile, get, patch, post, request } from "../api/http";
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";
import { getMessageTypeMeta, getReadStatusMeta, getStatusMeta } from "../utils/status";

const route = useRoute();
const loading = ref(false);
const dashboard = ref({});
const students = ref([]);
const teachers = ref([]);
const mentorApplications = ref([]);
const organizations = ref([]);
const internshipApplications = ref([]);
const forms = ref([]);
const evaluations = ref([]);
const reports = ref({});
const reportCenter = ref({});
const messages = ref([]);
const alerts = ref([]);
const section = computed(() => route.meta.section || "dashboard");

const studentDialog = ref(false);
const teacherDialog = ref(false);
const organizationDialog = ref(false);
const mentorDialog = ref(false);
const internshipDialog = ref(false);
const archiveDialog = ref(false);
const evaluationDialog = ref(false);
const currentRow = ref(null);
const archiveDialogMode = ref("single");
const archiveSelection = ref([]);
const studentImporting = ref(false);
const teacherImporting = ref(false);
const studentImportInput = ref(null);
const teacherImportInput = ref(null);
const latestStudentImportResult = ref(null);
const latestTeacherImportResult = ref(null);

const pageSize = 5;

const studentForm = reactive({ name: "", studentNo: "", major: "", className: "", phone: "", internshipType: "TEACHING" });
const teacherForm = reactive({ name: "", employeeNo: "", department: "", phone: "" });
const organizationForm = reactive({ name: "", address: "", contactName: "", contactPhone: "", nature: "", cooperationStatus: "合作中" });
const mentorReviewForm = reactive({ approved: true, comment: "" });
const internshipReviewForm = reactive({
  approved: true,
  organizationConfirmation: "待确认",
  organizationFeedback: "",
  receivedAt: "",
  comment: "",
});
const archiveReviewForm = reactive({ approved: true, score: 90, comment: "" });
const evaluationReviewForm = reactive({ collegeScore: 90, collegeComment: "" });

const pendingMentorApplications = computed(() => mentorApplications.value.filter((item) => item.status === "待学院复核"));
const pendingInternshipApplications = computed(() => internshipApplications.value.filter((item) => item.status === "待学院审批"));
const archiveForms = computed(() => forms.value.filter((item) => ["学院审核中", "已归档", "学院退回"].includes(item.status)));
const riskForms = computed(() => forms.value.filter((item) => ["教师退回", "学院退回"].includes(item.status)));
const pendingEvaluationRecords = computed(() => evaluations.value.filter((item) => item.submittedToCollege && !item.confirmedByCollege));
const actionableArchiveRows = computed(() => archiveSelection.value.filter((item) => item.status === "学院审核中"));
const archivableArchiveCount = computed(() => archiveForms.value.filter((item) => item.status === "学院审核中").length);
const unreadCollegeMessages = computed(() => messages.value.filter((item) => !item.read));
const messageReadFilter = ref("ALL");
const messageReadOptions = [
  { label: "全部消息", value: "ALL" },
  { label: "只看未读", value: "UNREAD" },
  { label: "只看已读", value: "READ" },
];
const filteredMessageSource = computed(() => {
  if (messageReadFilter.value === "UNREAD") {
    return messages.value.filter((item) => !item.read);
  }
  if (messageReadFilter.value === "READ") {
    return messages.value.filter((item) => item.read);
  }
  return messages.value;
});

function canConfirmEvaluation(row) {
  return row?.submittedToCollege && !row?.confirmedByCollege;
}

const {
  keyword: studentKeyword,
  currentPage: studentPage,
  filteredItems: filteredStudents,
  pagedItems: pagedStudents,
} = useFilteredPagination({
  source: students,
  matcher: (item) => [item.name, item.studentNo, item.major, item.className, item.internshipStatus],
  pageSize,
});
const {
  keyword: teacherKeyword,
  currentPage: teacherPage,
  filteredItems: filteredTeachers,
  pagedItems: pagedTeachers,
} = useFilteredPagination({
  source: teachers,
  matcher: (item) => [item.name, item.employeeNo, item.department, item.phone],
  pageSize,
});
const {
  keyword: mentorKeyword,
  currentPage: mentorPage,
  filteredItems: filteredMentorApplications,
  pagedItems: pagedMentorApplications,
} = useFilteredPagination({
  source: pendingMentorApplications,
  matcher: (item) => [item.student?.name, item.student?.studentNo, item.teacher?.name, item.studentRemark],
  pageSize,
});
const {
  keyword: organizationKeyword,
  currentPage: organizationPage,
  filteredItems: filteredOrganizations,
  pagedItems: pagedOrganizations,
} = useFilteredPagination({
  source: organizations,
  matcher: (item) => [item.name, item.address, item.contactName, item.contactPhone, item.nature, item.cooperationStatus],
  pageSize,
});
const {
  keyword: internshipKeyword,
  currentPage: internshipPage,
  filteredItems: filteredInternships,
  pagedItems: pagedInternships,
} = useFilteredPagination({
  source: pendingInternshipApplications,
  matcher: (item) => [item.student?.name, item.student?.studentNo, item.organization?.name, item.position, item.status],
  pageSize,
});
const {
  keyword: archiveKeyword,
  currentPage: archivePage,
  filteredItems: filteredArchives,
  pagedItems: pagedArchives,
} = useFilteredPagination({
  source: archiveForms,
  matcher: (item) => [item.studentName, item.studentNo, item.templateName, item.status, item.content?.title, item.content?.summary],
  pageSize,
});
const {
  keyword: messageKeyword,
  currentPage: messagePage,
  filteredItems: filteredMessages,
  pagedItems: pagedMessages,
} = useFilteredPagination({
  source: filteredMessageSource,
  matcher: (item) => [item.type, item.title, item.content],
  pageSize,
});
const {
  keyword: evaluationKeyword,
  currentPage: evaluationPage,
  filteredItems: filteredEvaluations,
  pagedItems: pagedEvaluations,
} = useFilteredPagination({
  source: evaluations,
  matcher: (item) => [
    item.student?.name,
    item.student?.studentNo,
    item.teacher?.name,
    item.stageComment,
    item.summaryComment,
    item.strengthsComment,
    item.improvementComment,
    item.confirmedByCollege ? "已确认" : "待确认",
  ],
  pageSize,
});
const {
  keyword: alertKeyword,
  currentPage: alertPage,
  filteredItems: filteredAlerts,
  pagedItems: pagedAlerts,
} = useFilteredPagination({
  source: alerts,
  matcher: (item) => [item.category, item.title, item.content, item.targetName, item.level],
  pageSize,
});

function attachmentList(row) {
  return Array.isArray(row?.attachments) ? row.attachments : [];
}

async function handleDownloadAttachment(item) {
  try {
    await downloadFile(item.downloadUrl || ("/files/" + item.storedName), item.name || "attachment");
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function loadAll() {
  loading.value = true;
  try {
    const [dashboardData, studentData, teacherData, mentorData, organizationData, internshipData, formData, evaluationData, reportData, reportCenterData, messageData, alertData] =
      await Promise.all([
        get("/dashboard"),
        get("/students"),
        get("/teachers"),
        get("/mentor-applications"),
        get("/organizations"),
        get("/internship-applications"),
        get("/forms"),
        get("/evaluations"),
        get("/reports/summary"),
        get("/reports/center"),
        get("/messages"),
        get("/risk-alerts"),
      ]);
    dashboard.value = dashboardData;
    students.value = studentData;
    teachers.value = teacherData;
    mentorApplications.value = mentorData;
    organizations.value = organizationData;
    internshipApplications.value = internshipData;
    forms.value = formData;
    evaluations.value = evaluationData;
    reports.value = reportData;
    reportCenter.value = reportCenterData;
    messages.value = messageData;
    alerts.value = alertData;
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}

function resetStudentForm() {
  Object.assign(studentForm, { name: "", studentNo: "", major: "", className: "", phone: "", internshipType: "TEACHING" });
}
function resetTeacherForm() {
  Object.assign(teacherForm, { name: "", employeeNo: "", department: "", phone: "" });
}
function resetOrganizationForm() {
  Object.assign(organizationForm, { name: "", address: "", contactName: "", contactPhone: "", nature: "", cooperationStatus: "合作中" });
}

function buildCsvLine(values) {
  return values
    .map((value) => {
      const cell = String(value ?? "").replace(/"/g, '""');
      return '"' + cell + '"';
    })
    .join(",");
}

function triggerBlobDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function downloadCsvTemplate(filename, headers, rows) {
  const csv = [buildCsvLine(headers), ...rows.map((row) => buildCsvLine(row))].join("\n");
  const blob = new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8;" });
  triggerBlobDownload(blob, filename);
}

function downloadExcelTable(filename, headers, rows, options = {}) {
  const title = options.title ? `<div style="font-size:16px;font-weight:700;margin-bottom:10px;color:#0f172a;">${escapeHtml(options.title)}</div>` : "";
  const summary = options.summary ? `<div style="margin-bottom:12px;color:#475569;">${escapeHtml(options.summary)}</div>` : "";
  const headerHtml = headers.map((item) => `<th style="background:#0f766e;color:#ffffff;border:1px solid #cbd5e1;padding:8px 10px;text-align:left;">${escapeHtml(item)}</th>`).join("");
  const bodyHtml = rows.map((row, rowIndex) => {
    const rowStyle = typeof options.rowStyleResolver === "function" ? options.rowStyleResolver(row, rowIndex) : {};
    const cells = row.map((cell, cellIndex) => {
      const cellStyle = rowStyle.cells?.[cellIndex] || rowStyle.defaultCellStyle || "border:1px solid #cbd5e1;padding:8px 10px;mso-number-format:'\\@';";
      return `<td style="${cellStyle}">${escapeHtml(cell)}</td>`;
    }).join("");
    const trStyle = rowStyle.rowStyle ? ` style="${rowStyle.rowStyle}"` : "";
    return `<tr${trStyle}>${cells}</tr>`;
  }).join("");
  const html = `<!DOCTYPE html><html><head><meta charset="UTF-8" /></head><body>${title}${summary}<table>${headerHtml ? `<thead><tr>${headerHtml}</tr></thead>` : ""}<tbody>${bodyHtml}</tbody></table></body></html>`;
  const blob = new Blob(["\uFEFF" + html], { type: "application/vnd.ms-excel;charset=utf-8;" });
  triggerBlobDownload(blob, filename);
}

async function downloadStudentImportTemplate() {
  try {
    await downloadFile("/students/import-template", "student-import-template.xlsx");
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function downloadTeacherImportTemplate() {
  try {
    await downloadFile("/teachers/import-template", "teacher-import-template.xlsx");
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function triggerStudentImport() {
  studentImportInput.value?.click();
}

function triggerTeacherImport() {
  teacherImportInput.value?.click();
}

function extractImportRows(result, failedOnly = false) {
  const details = Array.isArray(result?.details) ? result.details : [];
  const rows = failedOnly ? details.filter((item) => item.status === "FAILED") : details;
  if (rows.length) {
    return rows.map((item) => [item.rowNumber ?? "", item.status ?? "", item.identifier ?? "", item.name ?? "", item.message ?? ""]);
  }
  return [["", "", "", "", result?.message || "暂无导入明细"]];
}

function hasImportFailures(result) {
  return Array.isArray(result?.details) && result.details.some((item) => item.status === "FAILED");
}

function resolveImportMeta(label) {
  if (label === "teacher" || label === "教师") {
    return { name: "教师", file: "teacher" };
  }
  return { name: "学生", file: "student" };
}

function downloadImportResult(label, result, failedOnly = false) {
  if (failedOnly && !hasImportFailures(result)) {
    ElMessage.warning("当前没有失败项可导出");
    return;
  }
  const meta = resolveImportMeta(label);
  const timestamp = new Date().toISOString().replace(/[-:TZ.]/g, "").slice(0, 14);
  const suffix = failedOnly ? "-failed" : "";
  const rows = extractImportRows(result, failedOnly);
  downloadCsvTemplate(meta.file + "-import-result" + suffix + "-" + timestamp + ".csv", ["行号", "状态", "账号", "姓名", "结果"], rows);
}

function downloadLatestStudentImportResult() {
  if (!latestStudentImportResult.value) {
    ElMessage.warning("暂无学生导入结果");
    return;
  }
  downloadImportResult("student", latestStudentImportResult.value);
}

function downloadImportResultExcel(label, result, failedOnly = false) {
  if (failedOnly && !hasImportFailures(result)) {
    ElMessage.warning("当前没有失败项可导出");
    return;
  }
  const meta = resolveImportMeta(label);
  const timestamp = new Date().toISOString().replace(/[-:TZ.]/g, "").slice(0, 14);
  const suffix = failedOnly ? "-failed" : "";
  const details = Array.isArray(result?.details) ? result.details : [];
  const scopedDetails = failedOnly ? details.filter((item) => item.status === "FAILED") : details;
  const fallbackRows = [{ rowNumber: "", status: failedOnly ? "FAILED" : "INFO", identifier: "", name: "", message: result?.message || "暂无导入明细" }];
  const rows = (scopedDetails.length ? scopedDetails : fallbackRows).map((item) => [item.rowNumber ?? "", item.status ?? "", item.identifier ?? "", item.name ?? "", item.message ?? ""]);
  downloadExcelTable(meta.file + "-import-result" + suffix + "-" + timestamp + ".xls", ["行号", "状态", "账号", "姓名", "结果"], rows, {
    title: `${meta.name}导入结果`,
    summary: `成功 ${Number(result?.successCount || 0)} 条，跳过 ${Number(result?.skippedCount || 0)} 条`,
    rowStyleResolver: (row) => {
      const status = row[1];
      if (status === "SUCCESS") {
        return {
          rowStyle: "background:#f0fdf4;",
          defaultCellStyle: "border:1px solid #bbf7d0;padding:8px 10px;color:#166534;mso-number-format:'\\@';",
          cells: {
            1: "border:1px solid #86efac;padding:8px 10px;background:#dcfce7;color:#166534;font-weight:700;mso-number-format:'\\@';",
          },
        };
      }
      if (status === "FAILED") {
        return {
          rowStyle: "background:#fef2f2;",
          defaultCellStyle: "border:1px solid #fecaca;padding:8px 10px;color:#991b1b;mso-number-format:'\\@';",
          cells: {
            1: "border:1px solid #fca5a5;padding:8px 10px;background:#fee2e2;color:#b91c1c;font-weight:700;mso-number-format:'\\@';",
            4: "border:1px solid #fca5a5;padding:8px 10px;background:#fff1f2;color:#9f1239;font-weight:700;mso-number-format:'\\@';",
          },
        };
      }
      return {
        rowStyle: "background:#eff6ff;",
        defaultCellStyle: "border:1px solid #bfdbfe;padding:8px 10px;color:#1d4ed8;mso-number-format:'\\@';",
      };
    },
  });
}

function downloadLatestStudentFailedResult() {
  if (!latestStudentImportResult.value) {
    ElMessage.warning("暂无学生导入结果");
    return;
  }
  downloadImportResult("student", latestStudentImportResult.value, true);
}

function downloadLatestStudentFailedResultExcel() {
  if (!latestStudentImportResult.value) {
    ElMessage.warning("暂无学生导入结果");
    return;
  }
  downloadImportResultExcel("student", latestStudentImportResult.value, true);
}

function downloadLatestTeacherImportResult() {
  if (!latestTeacherImportResult.value) {
    ElMessage.warning("暂无教师导入结果");
    return;
  }
  downloadImportResult("teacher", latestTeacherImportResult.value);
}

function downloadLatestTeacherFailedResult() {
  if (!latestTeacherImportResult.value) {
    ElMessage.warning("暂无教师导入结果");
    return;
  }
  downloadImportResult("teacher", latestTeacherImportResult.value, true);
}

function downloadLatestTeacherFailedResultExcel() {
  if (!latestTeacherImportResult.value) {
    ElMessage.warning("暂无教师导入结果");
    return;
  }
  downloadImportResultExcel("teacher", latestTeacherImportResult.value, true);
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

async function runBatchImport(url, file, labelMeta, loadingRef, resultRef) {
  const formData = new FormData();
  formData.append("file", file);
  loadingRef.value = true;
  try {
    const result = await request(url, {
      method: "POST",
      body: formData,
    });
    const successCount = Number(result?.successCount || 0);
    const skippedCount = Number(result?.skippedCount || 0);
    const summary = `${labelMeta.name}导入完成，成功 ${successCount} 条，跳过 ${skippedCount} 条`;
    const errors = (Array.isArray(result?.details) ? result.details : [])
      .filter((item) => item.status === "FAILED")
      .map((item) => `第${item.rowNumber || "-"}行${item.identifier ? `（${item.identifier}）` : ""}${item.name ? ` ${item.name}` : ""}：${item.message || "导入失败"}`);
    resultRef.value = result;
    downloadImportResult(labelMeta.file, result);
    if (errors.length) {
      ElMessage.warning(summary);
      const lines = errors.slice(0, 20).map((item) => `<div style="margin-bottom:8px;padding:8px 10px;border-radius:8px;border:1px solid #fecaca;background:#fef2f2;color:#b91c1c;line-height:1.5;">${escapeHtml(item)}</div>`);
      if (errors.length > 20) {
        lines.push(`<div style="margin-bottom:8px;padding:8px 10px;border-radius:8px;border:1px solid #fde68a;background:#fffbeb;color:#92400e;">${escapeHtml(`还有 ${errors.length - 20} 条失败记录未展开`)}</div>`);
      }
      lines.push(`<div style="padding:8px 10px;border-radius:8px;background:#ecfeff;border:1px solid #a5f3fc;color:#155e75;">系统已自动生成 CSV 和 Excel 导出结果，可在页面中继续下载。</div>`);
      await ElMessageBox.alert(lines.join(""), `${labelMeta.name}导入结果`, {
        confirmButtonText: "我知道了",
        dangerouslyUseHTMLString: true,
      });
    } else {
      ElMessage.success(summary);
    }
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loadingRef.value = false;
  }
}

async function handleStudentImportChange(event) {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file) {
    return;
  }
  if (!(file.name || "").match(/\.(csv|xls|xlsx)$/i)) {
    ElMessage.warning("请选择 CSV 或 Excel 文件");
    return;
  }
  await runBatchImport("/students/import", file, { name: "学生", file: "student" }, studentImporting, latestStudentImportResult);
}

async function handleTeacherImportChange(event) {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file) {
    return;
  }
  if (!(file.name || "").match(/\.(csv|xls|xlsx)$/i)) {
    ElMessage.warning("请选择 CSV 或 Excel 文件");
    return;
  }
  await runBatchImport("/teachers/import", file, { name: "教师", file: "teacher" }, teacherImporting, latestTeacherImportResult);
}

async function saveStudent() {
  if (!studentForm.name || !studentForm.studentNo || !studentForm.major || !studentForm.className || !studentForm.phone || !studentForm.internshipType) {
    ElMessage.warning("请完整填写学生信息");
    return;
  }
  try {
    await post("/students", { ...studentForm });
    ElMessage.success("学生创建成功，初始密码为 123456");
    studentDialog.value = false;
    resetStudentForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function saveTeacher() {
  if (!teacherForm.name || !teacherForm.employeeNo || !teacherForm.department || !teacherForm.phone) {
    ElMessage.warning("请完整填写教师信息");
    return;
  }
  try {
    await post("/teachers", { ...teacherForm });
    ElMessage.success("教师创建成功，初始密码为 123456");
    teacherDialog.value = false;
    resetTeacherForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function saveOrganization() {
  if (!organizationForm.name || !organizationForm.address || !organizationForm.contactName || !organizationForm.contactPhone || !organizationForm.nature || !organizationForm.cooperationStatus) {
    ElMessage.warning("请完整填写实习单位信息");
    return;
  }
  try {
    await post("/organizations", { ...organizationForm });
    ElMessage.success("实习单位已保存");
    organizationDialog.value = false;
    resetOrganizationForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
async function toggleStudentStatus(row) {
  try {
    const nextStatus = row.accountStatus === "ACTIVE" ? "DISABLED" : "ACTIVE";
    await patch(`/students/${row.id}/status`, { status: nextStatus });
    ElMessage.success(nextStatus === "ACTIVE" ? "学生账号已启用" : "学生账号已停用");
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function resetStudentPassword(row) {
  try {
    await post(`/students/${row.id}/reset-password`, {});
    ElMessage.success(`已将 ${row.name} 的密码重置为 123456`);
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function toggleTeacherStatus(row) {
  try {
    const nextStatus = row.accountStatus === "ACTIVE" ? "DISABLED" : "ACTIVE";
    await patch(`/teachers/${row.id}/status`, { status: nextStatus });
    ElMessage.success(nextStatus === "ACTIVE" ? "教师账号已启用" : "教师账号已停用");
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function resetTeacherPassword(row) {
  try {
    await post(`/teachers/${row.id}/reset-password`, {});
    ElMessage.success(`已将 ${row.name} 的密码重置为 123456`);
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openMentorReview(row) {
  currentRow.value = row;
  mentorReviewForm.approved = true;
  mentorReviewForm.comment = "";
  mentorDialog.value = true;
}
async function submitMentorReview() {
  try {
    await post(`/mentor-applications/${currentRow.value.id}/college-review`, { ...mentorReviewForm });
    ElMessage.success("指导申请审核完成");
    mentorDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openInternshipReview(row) {
  currentRow.value = row;
  internshipReviewForm.approved = true;
  internshipReviewForm.organizationConfirmation = row.organizationConfirmation || "待确认";
  internshipReviewForm.organizationFeedback = row.organizationFeedback || "";
  internshipReviewForm.receivedAt = row.receivedAt || "";
  internshipReviewForm.comment = row.reviewComment || "";
  internshipDialog.value = true;
}
async function submitInternshipReview() {
  if (!internshipReviewForm.organizationConfirmation) {
    ElMessage.warning("请填写单位确认情况");
    return;
  }
  try {
    await post(`/internship-applications/${currentRow.value.id}/review`, { ...internshipReviewForm });
    ElMessage.success("实习申请审核完成");
    internshipDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function handleArchiveSelectionChange(rows) {
  archiveSelection.value = rows;
}

function openArchiveReview(row) {
  archiveDialogMode.value = "single";
  currentRow.value = row;
  archiveReviewForm.approved = true;
  archiveReviewForm.score = row.score || 90;
  archiveReviewForm.comment = row.collegeComment || "";
  archiveDialog.value = true;
}

function openBatchArchiveReview(approved) {
  if (!actionableArchiveRows.value.length) {
    ElMessage.warning("当前没有可批量归档的表单");
    return;
  }
  archiveDialogMode.value = "batch";
  currentRow.value = null;
  archiveReviewForm.approved = approved;
  const scoredRows = actionableArchiveRows.value.filter((item) => typeof item.score === "number");
  archiveReviewForm.score = scoredRows.length
    ? Math.round(scoredRows.reduce((total, item) => total + item.score, 0) / scoredRows.length)
    : 90;
  archiveReviewForm.comment = "";
  archiveDialog.value = true;
}

function downloadCsv(filename, columns, rows) {
  const header = columns.map((column) => column.label).join(",");
  const body = rows
    .map((row) =>
      columns
        .map((column) => {
          const raw = row[column.key] ?? "";
          const value = String(raw).replace(/"/g, '""');
          return `"${value}"`;
        })
        .join(",")
    )
    .join("\n");
  const blob = new Blob([`\uFEFF${header}\n${body}`], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function exportArchiveLedger() {
  const rows = filteredArchives.value.map((item) => ({
    studentName: item.studentName,
    studentNo: item.studentNo,
    templateName: item.templateName,
    status: item.status,
    title: item.content?.title || "未填写标题",
    summary: item.content?.summary || "",
    score: item.score ?? "",
    updatedAt: item.updatedAt || "",
  }));
  if (!rows.length) {
    ElMessage.warning("暂无可导出的归档台账");
    return;
  }
  downloadCsv(
    "archive-ledger.csv",
    [
      { key: "studentName", label: "学生姓名" },
      { key: "studentNo", label: "学号" },
      { key: "templateName", label: "模板名称" },
      { key: "status", label: "状态" },
      { key: "title", label: "标题" },
      { key: "summary", label: "摘要" },
      { key: "score", label: "成绩" },
      { key: "updatedAt", label: "更新时间" },
    ],
    rows,
  );
}

function exportStudentReport() {
  const rows = [
    ...(reportCenter.value.students?.statusDistribution || []).map((item) => ({ category: "学生状态", label: item.label, count: item.count })),
    ...(reportCenter.value.students?.typeDistribution || []).map((item) => ({ category: "实习类型", label: item.label, count: item.count })),
  ];
  if (!rows.length) {
    ElMessage.warning("暂无可导出的学生统计数据");
    return;
  }
  downloadCsv(
    "student-report-center.csv",
    [
      { key: "category", label: "分类" },
      { key: "label", label: "名称" },
      { key: "count", label: "数量" },
    ],
    rows,
  );
}

function exportTeacherReport() {
  const rows = (reportCenter.value.teachers?.workload || []).map((item) => ({
    teacherName: item.teacherName,
    employeeNo: item.employeeNo,
    department: item.department,
    studentCount: item.studentCount,
    archivedCount: item.archivedCount,
    pendingArchiveCount: item.pendingArchiveCount,
    evaluationCount: item.evaluationCount,
    averageScore: item.averageScore,
  }));
  if (!rows.length) {
    ElMessage.warning("暂无可导出的教师工作量数据");
    return;
  }
  downloadCsv(
    "teacher-workload-report.csv",
    [
      { key: "teacherName", label: "教师姓名" },
      { key: "employeeNo", label: "工号" },
      { key: "department", label: "所属部门" },
      { key: "studentCount", label: "负责学生数" },
      { key: "archivedCount", label: "已归档" },
      { key: "pendingArchiveCount", label: "待归档" },
      { key: "evaluationCount", label: "评价数" },
      { key: "averageScore", label: "平均成绩" },
    ],
    rows,
  );
}

function exportTemplateReport() {
  const rows = (reportCenter.value.forms?.templateRanking || []).map((item) => ({
    templateName: item.templateName,
    category: item.category,
    total: item.total,
    archived: item.archived,
    pending: item.pending,
    averageScore: item.averageScore,
  }));
  if (!rows.length) {
    ElMessage.warning("暂无可导出的模板统计数据");
    return;
  }
  downloadCsv(
    "template-ranking-report.csv",
    [
      { key: "templateName", label: "模板名称" },
      { key: "category", label: "分类" },
      { key: "total", label: "总数" },
      { key: "archived", label: "已归档" },
      { key: "pending", label: "待归档" },
      { key: "averageScore", label: "平均成绩" },
    ],
    rows,
  );
}
async function submitArchiveReview() {
  if (Number(archiveReviewForm.score) < 0 || Number(archiveReviewForm.score) > 100) {
    ElMessage.warning("归档评分应在 0 到 100 之间");
    return;
  }
  try {
    if (archiveDialogMode.value === "batch") {
      if (!actionableArchiveRows.value.length) {
        ElMessage.warning("当前没有可批量处理的表单");
        return;
      }
      const result = await post("/forms/batch-college-review", {
        formIds: actionableArchiveRows.value.map((item) => item.id),
        approved: archiveReviewForm.approved,
        score: archiveReviewForm.score,
        comment: archiveReviewForm.comment,
      });
      const skippedCount = Number(result?.skippedCount || 0);
      ElMessage.success(`批量归档已处理 ${result?.processedCount || 0} 条${skippedCount ? `，跳过 ${skippedCount} 条` : ""}`);
      archiveSelection.value = [];
    } else {
      await post(`/forms/${currentRow.value.id}/college-review`, {
        approved: archiveReviewForm.approved,
        score: archiveReviewForm.score,
        comment: archiveReviewForm.comment,
      });
      ElMessage.success("归档审核已提交");
    }
    archiveDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openEvaluationReview(row) {
  if (!canConfirmEvaluation(row)) {
    ElMessage.warning("当前评价暂不能由学院确认");
    return;
  }
  currentRow.value = row;
  evaluationReviewForm.collegeScore = row.collegeScore ?? row.recommendedScore ?? row.finalScore ?? 90;
  evaluationReviewForm.collegeComment = row.collegeComment || "";
  evaluationDialog.value = true;
}

async function submitEvaluationReview() {
  if (Number(evaluationReviewForm.collegeScore) < 0 || Number(evaluationReviewForm.collegeScore) > 100) {
    ElMessage.warning("学院评分应在 0 到 100 之间");
    return;
  }
  try {
    await post(`/evaluations/${currentRow.value.id}/college-confirm`, { ...evaluationReviewForm });
    ElMessage.success("评价确认成功");
    evaluationDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function sendReminder(row) {
  try {
    await post(`/risk-alerts/${row.id}/remind`, {});
    ElMessage.success(row.remindActionLabel ? `${row.remindActionLabel}成功` : "提醒发送成功");
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function markRead(row) {
  try {
    await post(`/messages/${row.id}/read`, {});
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

onMounted(loadAll);
watch(() => route.path, loadAll);
watch(messageReadFilter, () => {
  messagePage.value = 1;
});
</script>
<template>
  <div class="page-shell" v-loading="loading">
    <template v-if="section === 'dashboard'">
      <div class="page-header">
        <div>
          <h2>学院工作台</h2>
          <div class="subtle">查看学院审批、归档、评价确认和风险提醒的整体进度。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>学生总数</h4><strong>{{ dashboard.studentCount || 0 }}</strong></div>
        <div class="metric-card"><h4>教师总数</h4><strong>{{ dashboard.teacherCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待复核指导申请</h4><strong>{{ dashboard.pendingMentorReviewCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待审批实习申请</h4><strong>{{ dashboard.pendingInternshipReviewCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待归档表单</h4><strong>{{ dashboard.pendingArchiveCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待确认评价</h4><strong>{{ pendingEvaluationRecords.length }}</strong></div>
        <div class="metric-card"><h4>风险学生数</h4><strong>{{ dashboard.riskStudentCount || 0 }}</strong></div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header">
            <h2 style="font-size: 20px">待办概览</h2>
            <el-tag type="warning">{{ pendingMentorApplications.length + pendingInternshipApplications.length + riskForms.length }}</el-tag>
          </div>
          <el-space wrap>
            <el-tag type="warning">待复核指导申请 {{ pendingMentorApplications.length }}</el-tag>
            <el-tag type="primary">待审批实习申请 {{ pendingInternshipApplications.length }}</el-tag>
            <el-tag type="danger">退回风险表单 {{ riskForms.length }}</el-tag>
          </el-space>
        </div>
        <div class="panel-card">
          <div class="page-header">
            <h2 style="font-size: 20px">学院概览</h2>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="申请中学生">{{ reports.students?.applied || 0 }}</el-descriptions-item>
            <el-descriptions-item label="在岗学生">{{ reports.students?.active || 0 }}</el-descriptions-item>
            <el-descriptions-item label="有效指导关系">{{ reports.teachers?.activeGuidanceCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="归档率">{{ reports.forms?.archiveRate || 0 }}%</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </template>

    <template v-else-if="section === 'students'">
      <div class="page-header">
        <div>
          <h2>学生基础管理</h2>
          <div class="subtle">维护学生账号、基础信息和批量导入结果。</div>
        </div>
        <div style="display: flex; gap: 12px; flex-wrap: wrap">
          <el-button plain @click="downloadStudentImportTemplate">下载导入模板</el-button>
          <el-button plain :loading="studentImporting" @click="triggerStudentImport">批量导入学生</el-button>
          <el-button plain :disabled="!latestStudentImportResult" @click="downloadLatestStudentImportResult">下载最近结果</el-button>
          <el-button plain :disabled="!latestStudentImportResult || !hasImportFailures(latestStudentImportResult)" @click="downloadLatestStudentFailedResult">仅导出失败项 CSV</el-button>
          <el-button plain :disabled="!latestStudentImportResult || !hasImportFailures(latestStudentImportResult)" @click="downloadLatestStudentFailedResultExcel">仅导出失败项 Excel</el-button>
          <el-button type="primary" color="#0f766e" @click="studentDialog = true">新增学生</el-button>
        </div>
      </div>
      <input ref="studentImportInput" type="file" accept=".csv,.xlsx,.xls,text/csv,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" style="display: none" @change="handleStudentImportChange" />
      <FilterTablePanel v-model:keyword="studentKeyword" v-model:current-page="studentPage" placeholder="筛选学生姓名、学号、专业、班级" :total="filteredStudents.length" :page-size="pageSize">
        <el-table :data="pagedStudents" style="margin-top: 16px">
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="studentNo" label="学号" width="130" />
          <el-table-column prop="major" label="专业" min-width="160" />
          <el-table-column prop="className" label="班级" min-width="140" />
          <el-table-column prop="internshipType" label="实习类型" width="120" />
          <el-table-column label="实习状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.internshipStatus).type">{{ getStatusMeta(row.internshipStatus).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="账号状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.accountStatus === 'ACTIVE' ? 'success' : 'danger'">{{ row.accountStatus === "ACTIVE" ? "启用" : "停用" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="toggleStudentStatus(row)">{{ row.accountStatus === "ACTIVE" ? "停用账号" : "启用账号" }}</el-button>
              <el-button link type="primary" @click="resetStudentPassword(row)">重置密码</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无学生数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'teachers'">
      <div class="page-header">
        <div>
          <h2>教师基础管理</h2>
          <div class="subtle">维护教师账号、带教人数和批量导入结果。</div>
        </div>
        <div style="display: flex; gap: 12px; flex-wrap: wrap">
          <el-button plain @click="downloadTeacherImportTemplate">下载导入模板</el-button>
          <el-button plain :loading="teacherImporting" @click="triggerTeacherImport">批量导入教师</el-button>
          <el-button plain :disabled="!latestTeacherImportResult" @click="downloadLatestTeacherImportResult">下载最近结果</el-button>
          <el-button plain :disabled="!latestTeacherImportResult || !hasImportFailures(latestTeacherImportResult)" @click="downloadLatestTeacherFailedResult">仅导出失败项 CSV</el-button>
          <el-button plain :disabled="!latestTeacherImportResult || !hasImportFailures(latestTeacherImportResult)" @click="downloadLatestTeacherFailedResultExcel">仅导出失败项 Excel</el-button>
          <el-button type="primary" color="#0f766e" @click="teacherDialog = true">新增教师</el-button>
        </div>
      </div>
      <input ref="teacherImportInput" type="file" accept=".csv,.xlsx,.xls,text/csv,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" style="display: none" @change="handleTeacherImportChange" />
      <FilterTablePanel v-model:keyword="teacherKeyword" v-model:current-page="teacherPage" placeholder="筛选教师姓名、工号、部门" :total="filteredTeachers.length" :page-size="pageSize">
        <el-table :data="pagedTeachers" style="margin-top: 16px">
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="employeeNo" label="工号" width="130" />
          <el-table-column prop="department" label="所属部门" min-width="180" />
          <el-table-column prop="phone" label="联系电话" width="140" />
          <el-table-column prop="studentCount" label="负责学生数" width="120" />
          <el-table-column label="账号状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.accountStatus === 'ACTIVE' ? 'success' : 'danger'">{{ row.accountStatus === "ACTIVE" ? "启用" : "停用" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="toggleTeacherStatus(row)">{{ row.accountStatus === "ACTIVE" ? "停用账号" : "启用账号" }}</el-button>
              <el-button link type="primary" @click="resetTeacherPassword(row)">重置密码</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无教师数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>
    <template v-else-if="section === 'mentor'">
      <div class="page-header">
        <div>
          <h2>指导申请复核</h2>
          <div class="subtle">复核教师已处理的指导申请，并确认学院侧的指导关系。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="mentorKeyword" v-model:current-page="mentorPage" placeholder="筛选学生、教师和申请说明" :total="filteredMentorApplications.length" :page-size="pageSize">
        <el-table :data="pagedMentorApplications" style="margin-top: 16px">
          <el-table-column label="学生" min-width="160"><template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template></el-table-column>
          <el-table-column label="指导教师" min-width="160"><template #default="{ row }">{{ row.teacher?.name }} / {{ row.teacher?.employeeNo }}</template></el-table-column>
          <el-table-column prop="studentRemark" label="学生申请说明" min-width="220" />
          <el-table-column prop="teacherRemark" label="教师处理意见" min-width="180" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openMentorReview(row)">去复核</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无待学院复核的指导申请" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'organizations'">
      <div class="page-header">
        <div>
          <h2>实习单位管理</h2>
          <div class="subtle">维护学院可选实习单位和合作状态，支撑学生实习申请审核。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="organizationDialog = true">新增单位</el-button>
      </div>
      <FilterTablePanel v-model:keyword="organizationKeyword" v-model:current-page="organizationPage" placeholder="筛选单位名称、联系人、地址" :total="filteredOrganizations.length" :page-size="pageSize">
        <el-table :data="pagedOrganizations" style="margin-top: 16px">
          <el-table-column prop="name" label="单位名称" min-width="180" />
          <el-table-column prop="address" label="单位地址" min-width="220" />
          <el-table-column prop="contactName" label="联系人" width="110" />
          <el-table-column prop="contactPhone" label="联系电话" width="140" />
          <el-table-column prop="nature" label="单位性质" min-width="140" />
          <el-table-column label="合作状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.cooperationStatus).type">{{ getStatusMeta(row.cooperationStatus).label }}</el-tag>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无实习单位数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'internship'">
      <div class="page-header">
        <div>
          <h2>实习申请审批</h2>
          <div class="subtle">查看学生提交的实习申请、单位确认信息和附件材料。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="internshipKeyword" v-model:current-page="internshipPage" placeholder="筛选学生、批次、单位、岗位" :total="filteredInternships.length" :page-size="pageSize">
        <el-table :data="pagedInternships" style="margin-top: 16px">
          <el-table-column label="学生" min-width="150"><template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template></el-table-column>
          <el-table-column prop="batchName" label="批次" width="150" />
          <el-table-column label="实习单位" min-width="180"><template #default="{ row }">{{ row.organization?.name }}</template></el-table-column>
          <el-table-column prop="position" label="岗位" min-width="150" />
          <el-table-column prop="gradeTarget" label="年级学段" width="120" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.status).type">{{ getStatusMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="附件" min-width="220">
            <template #default="{ row }">
              <el-space v-if="attachmentList(row).length" wrap>
                <el-button
                  v-for="item in attachmentList(row)"
                  :key="item.id || item.storedName || item.name"
                  link
                  type="primary"
                  @click="handleDownloadAttachment(item)"
                >
                  {{ item.name || "附件" }}
                </el-button>
              </el-space>
              <span v-else class="subtle">暂无附件</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openInternshipReview(row)">去审批</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无待审批的实习申请" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'archive'">
      <div class="page-header">
        <div>
          <h2>表单归档中心</h2>
          <div class="subtle">统一处理学院审核中的表单，支持批量归档、批量退回和台账导出。</div>
        </div>
        <div style="display: grid; gap: 10px; justify-items: end">
          <el-space wrap>
            <el-tag type="warning">待归档 {{ archivableArchiveCount }} 条</el-tag>
            <el-tag type="info">已选 {{ actionableArchiveRows.length }} 条</el-tag>
          </el-space>
          <div style="display: flex; gap: 12px; flex-wrap: wrap">
            <el-button plain @click="exportArchiveLedger">导出归档台账</el-button>
            <el-button type="warning" plain :disabled="!actionableArchiveRows.length" @click="openBatchArchiveReview(false)">批量退回</el-button>
            <el-button type="primary" color="#0f766e" :disabled="!actionableArchiveRows.length" @click="openBatchArchiveReview(true)">批量归档</el-button>
          </div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="archiveKeyword" v-model:current-page="archivePage" placeholder="筛选学生、表单模板、标题、状态" :total="filteredArchives.length" :page-size="pageSize">
        <el-space wrap style="margin-bottom: 12px">
          <el-tag type="warning">仅状态为“学院审核中”的表单支持批量处理</el-tag>
          <el-tag type="info">当前页 {{ pagedArchives.length }} 条</el-tag>
        </el-space>
        <el-table :data="pagedArchives" style="margin-top: 16px" @selection-change="handleArchiveSelectionChange">
          <el-table-column type="selection" width="50" :selectable="(row) => row.status === '学院审核中'" />
          <el-table-column label="学生" min-width="160"><template #default="{ row }">{{ row.studentName }} / {{ row.studentNo }}</template></el-table-column>
          <el-table-column prop="templateName" label="表单模板" min-width="160" />
          <el-table-column prop="mentorTeacherName" label="指导教师" min-width="120" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.status).type">{{ getStatusMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="内容摘要" min-width="260">
            <template #default="{ row }">
              <div>{{ row.content?.title || "未填写标题" }}</div>
              <div class="subtle">{{ row.content?.summary || "暂无摘要" }}</div>
            </template>
          </el-table-column>
          <el-table-column label="附件" min-width="220">
            <template #default="{ row }">
              <el-space v-if="attachmentList(row).length" wrap>
                <el-button
                  v-for="item in attachmentList(row)"
                  :key="item.id || item.storedName || item.name"
                  link
                  type="primary"
                  @click="handleDownloadAttachment(item)"
                >
                  {{ item.name || "附件" }}
                </el-button>
              </el-space>
              <span v-else class="subtle">暂无附件</span>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="成绩" width="90" />
          <el-table-column label="审批" width="120">
            <template #default="{ row }">
              <el-button v-if="row.status === '学院审核中'" link type="primary" @click="openArchiveReview(row)">归档处理</el-button>
              <span v-else class="subtle">无需处理</span>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无归档数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'evaluations'">
      <div class="page-header">
        <div>
          <h2>评价确认</h2>
          <div class="subtle">查看教师提交的实习评价，并完成学院终审确认。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="evaluationKeyword" v-model:current-page="evaluationPage" placeholder="筛选学生、教师、评价内容和确认状态" :total="filteredEvaluations.length" :page-size="pageSize">
        <el-table :data="pagedEvaluations" style="margin-top: 16px">
          <el-table-column label="学生" min-width="160"><template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template></el-table-column>
          <el-table-column label="指导教师" width="130"><template #default="{ row }">{{ row.teacher?.name }}</template></el-table-column>
          <el-table-column label="维度评分" min-width="260"><template #default="{ row }"><el-space wrap><el-tag v-for="item in row.dimensionScores || []" :key="`${row.id}-${item.key}`" type="success">{{ item.label }} {{ item.score }}</el-tag></el-space></template></el-table-column>
          <el-table-column prop="recommendedScore" label="教师建议分" width="100" />
          <el-table-column prop="finalScore" label="最终成绩" width="100" />
          <el-table-column label="确认状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.confirmedByCollege ? 'success' : 'warning'">{{ row.confirmedByCollege ? "已确认" : "待确认" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="canConfirmEvaluation(row)" link type="primary" @click="openEvaluationReview(row)">确认评价</el-button>
              <span v-else class="subtle">无需处理</span>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无评价记录" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'reports'">
      <div class="page-header">
        <div>
          <h2>统计报表</h2>
          <div class="subtle">查看学院整体进度、模板排名、教师工作量和趋势统计。</div>
        </div>
        <div style="display: flex; gap: 12px; flex-wrap: wrap">
          <el-button plain @click="exportStudentReport">导出学生统计</el-button>
          <el-button plain @click="exportTeacherReport">导出教师工作量</el-button>
          <el-button type="primary" plain color="#0f766e" @click="exportTemplateReport">导出模板统计</el-button>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>学生总数</h4><strong>{{ reportCenter.overview?.studentCount || 0 }}</strong></div>
        <div class="metric-card"><h4>教师总数</h4><strong>{{ reportCenter.overview?.teacherCount || 0 }}</strong></div>
        <div class="metric-card"><h4>单位数量</h4><strong>{{ reportCenter.overview?.organizationCount || 0 }}</strong></div>
        <div class="metric-card"><h4>表单总数</h4><strong>{{ reportCenter.overview?.formCount || 0 }}</strong></div>
        <div class="metric-card"><h4>已归档</h4><strong>{{ reportCenter.overview?.archivedCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待归档</h4><strong>{{ reportCenter.overview?.pendingArchiveCount || 0 }}</strong></div>
        <div class="metric-card"><h4>已确认评价</h4><strong>{{ reportCenter.overview?.confirmedEvaluationCount || 0 }}</strong></div>
        <div class="metric-card"><h4>平均成绩</h4><strong>{{ reportCenter.overview?.averageScore || 0 }}</strong></div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">学生状态分布</h2><el-tag>{{ reportCenter.students?.statusDistribution?.length || 0 }} 项</el-tag></div>
          <el-table :data="reportCenter.students?.statusDistribution || []" style="margin-top: 16px">
            <el-table-column prop="label" label="状态" />
            <el-table-column prop="count" label="数量" width="100" />
            <template #empty><el-empty description="暂无学生状态统计" /></template>
          </el-table>
        </div>
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">表单状态分布</h2><el-tag type="success">归档率 {{ reportCenter.overview?.archiveRate || 0 }}%</el-tag></div>
          <el-table :data="reportCenter.forms?.statusDistribution || []" style="margin-top: 16px">
            <el-table-column prop="label" label="状态" />
            <el-table-column prop="count" label="数量" width="100" />
            <template #empty><el-empty description="暂无表单状态统计" /></template>
          </el-table>
        </div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">模板排名</h2><el-tag type="warning">{{ reportCenter.forms?.templateRanking?.length || 0 }} 个模板</el-tag></div>
          <el-table :data="reportCenter.forms?.templateRanking || []" style="margin-top: 16px">
            <el-table-column prop="templateName" label="模板名称" min-width="160" />
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column prop="total" label="总数" width="80" />
            <el-table-column prop="archived" label="已归档" width="90" />
            <el-table-column prop="pending" label="待归档" width="90" />
            <el-table-column prop="averageScore" label="平均成绩" width="90" />
            <template #empty><el-empty description="暂无模板统计数据" /></template>
          </el-table>
        </div>
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">教师工作量</h2><el-tag type="info">{{ reportCenter.teachers?.workload?.length || 0 }} 位教师</el-tag></div>
          <el-table :data="reportCenter.teachers?.workload || []" style="margin-top: 16px">
            <el-table-column prop="teacherName" label="教师姓名" min-width="140" />
            <el-table-column prop="studentCount" label="学生数" width="90" />
            <el-table-column prop="archivedCount" label="已归档" width="90" />
            <el-table-column prop="pendingArchiveCount" label="待归档" width="90" />
            <el-table-column prop="evaluationCount" label="评价数" width="90" />
            <el-table-column prop="averageScore" label="平均成绩" width="90" />
            <template #empty><el-empty description="暂无教师工作量数据" /></template>
          </el-table>
        </div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">评价汇总</h2><el-tag type="success">待确认 {{ reportCenter.evaluations?.summary?.pending || 0 }}</el-tag></div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="评价总数">{{ reportCenter.evaluations?.summary?.total || 0 }}</el-descriptions-item>
            <el-descriptions-item label="已确认">{{ reportCenter.evaluations?.summary?.confirmed || 0 }}</el-descriptions-item>
            <el-descriptions-item label="待确认">{{ reportCenter.evaluations?.summary?.pending || 0 }}</el-descriptions-item>
            <el-descriptions-item label="平均成绩">{{ reportCenter.evaluations?.summary?.averageScore || 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-space wrap style="margin-top: 16px">
            <el-tag v-for="item in reportCenter.evaluations?.scoreDistribution || []" :key="item.label" type="success">{{ item.label }} {{ item.count }}</el-tag>
          </el-space>
        </div>
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">单位使用情况</h2><el-tag>{{ reportCenter.organizations?.usageRanking?.length || 0 }} 项</el-tag></div>
          <el-table :data="reportCenter.organizations?.usageRanking || []" style="margin-top: 16px">
            <el-table-column prop="organizationName" label="实习单位" min-width="180" />
            <el-table-column prop="cooperationStatus" label="合作状态" min-width="120" />
            <el-table-column prop="count" label="使用次数" width="100" />
            <template #empty><el-empty description="暂无单位使用统计" /></template>
          </el-table>
        </div>
      </div>
      <div class="panel-card">
        <div class="page-header"><h2 style="font-size: 20px">月度趋势</h2><el-tag type="warning">提交 / 归档 / 评价</el-tag></div>
        <el-table :data="reportCenter.trends || []" style="margin-top: 16px">
          <el-table-column prop="month" label="月份" width="120" />
          <el-table-column prop="submitted" label="提交数" width="120" />
          <el-table-column prop="archived" label="归档数" width="120" />
          <el-table-column prop="evaluated" label="评价数" width="120" />
          <template #empty><el-empty description="暂无月度趋势数据" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'alerts'">
      <div class="page-header">
        <div>
          <h2>风险提醒</h2>
          <div class="subtle">查看逾期、退回和待处理风险项，并向相关角色发送提醒。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="alertKeyword" v-model:current-page="alertPage" placeholder="筛选分类、标题、目标对象" :total="filteredAlerts.length" :page-size="pageSize">
        <el-table :data="pagedAlerts" style="margin-top: 16px">
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column label="级别" width="100"><template #default="{ row }"><el-tag :type="row.level === 'danger' ? 'danger' : 'warning'">{{ row.level === 'danger' ? "高" : "中" }}</el-tag></template></el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column prop="targetName" label="对象" width="120" />
          <el-table-column prop="overdueDays" label="逾期天数" width="100" />
          <el-table-column label="操作" width="140"><template #default="{ row }"><el-button v-if="row.remindable" link type="primary" @click="sendReminder(row)">{{ row.remindActionLabel || "发送提醒" }}</el-button><span v-else class="subtle">无需提醒</span></template></el-table-column>
          <template #empty><el-empty description="暂无风险提醒" /></template>
        </el-table>
      </FilterTablePanel>
    </template>
    <template v-else-if="section === 'messages'">
      <div class="page-header">
        <div>
          <h2>消息中心</h2>
          <div class="subtle">集中查看学院待办、催办提醒和系统反馈消息。</div>
        </div>
        <el-tag type="warning">未读 {{ unreadCollegeMessages.length }} 条</el-tag>
      </div>
      <FilterTablePanel v-model:keyword="messageKeyword" v-model:current-page="messagePage" placeholder="筛选类型、标题、内容" :total="filteredMessages.length" :page-size="pageSize">
        <template #toolbar-extra>
          <el-select v-model="messageReadFilter" style="width: 140px">
            <el-option v-for="option in messageReadOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </template>
        <el-table :data="pagedMessages" style="margin-top: 16px">
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getMessageTypeMeta(row.type).type">{{ getMessageTypeMeta(row.type).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="getReadStatusMeta(row.read).type">{{ getReadStatusMeta(row.read).label }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="!row.read" link type="primary" @click="markRead(row)">标记已读</el-button><span v-else class="subtle">已处理</span></template></el-table-column>
          <template #empty><el-empty description="暂无消息数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <el-dialog v-model="studentDialog" title="新增学生" width="520px">
      <el-form label-position="top">
        <el-form-item label="姓名"><el-input v-model="studentForm.name" /></el-form-item>
        <el-form-item label="学号"><el-input v-model="studentForm.studentNo" /></el-form-item>
        <el-form-item label="专业"><el-input v-model="studentForm.major" /></el-form-item>
        <el-form-item label="班级"><el-input v-model="studentForm.className" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="studentForm.phone" /></el-form-item>
        <el-form-item label="实习类型">
          <el-select v-model="studentForm.internshipType" style="width: 100%">
            <el-option label="任课实习" value="TEACHING" />
            <el-option label="班主任实习" value="HEAD_TEACHER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="studentDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="saveStudent">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="teacherDialog" title="新增教师" width="520px">
      <el-form label-position="top">
        <el-form-item label="姓名"><el-input v-model="teacherForm.name" /></el-form-item>
        <el-form-item label="工号"><el-input v-model="teacherForm.employeeNo" /></el-form-item>
        <el-form-item label="所属部门"><el-input v-model="teacherForm.department" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="teacherForm.phone" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="teacherDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="saveTeacher">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="organizationDialog" title="新增实习单位" width="560px">
      <el-form label-position="top">
        <el-form-item label="单位名称"><el-input v-model="organizationForm.name" /></el-form-item>
        <el-form-item label="单位地址"><el-input v-model="organizationForm.address" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="organizationForm.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="organizationForm.contactPhone" /></el-form-item>
        <el-form-item label="单位性质"><el-input v-model="organizationForm.nature" /></el-form-item>
        <el-form-item label="合作状态"><el-input v-model="organizationForm.cooperationStatus" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="organizationDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="saveOrganization">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="mentorDialog" title="指导申请复核" width="520px">
      <el-form label-position="top">
        <el-form-item label="审核结果"><el-switch v-model="mentorReviewForm.approved" inline-prompt active-text="通过" inactive-text="驳回" /></el-form-item>
        <el-form-item label="学院意见"><el-input v-model="mentorReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="mentorDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitMentorReview">提交</el-button></template>
    </el-dialog>
    <el-dialog v-model="internshipDialog" title="实习申请审批" width="560px">
      <el-form label-position="top">
        <el-form-item v-if="attachmentList(currentRow).length" label="申请附件">
          <div style="display: grid; gap: 8px; width: 100%">
            <div
              v-for="item in attachmentList(currentRow)"
              :key="item.id || item.storedName || item.name"
              style="display: flex; justify-content: space-between; align-items: center; gap: 12px"
            >
              <span>{{ item.name || "附件" }}</span>
              <el-button link type="primary" @click="handleDownloadAttachment(item)">下载</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="审批结果"><el-switch v-model="internshipReviewForm.approved" inline-prompt active-text="通过" inactive-text="退回" /></el-form-item>
        <el-form-item label="单位确认情况"><el-input v-model="internshipReviewForm.organizationConfirmation" /></el-form-item>
        <el-form-item label="单位反馈"><el-input v-model="internshipReviewForm.organizationFeedback" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="单位回执日期"><el-date-picker v-model="internshipReviewForm.receivedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="学院意见"><el-input v-model="internshipReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="internshipDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitInternshipReview">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="archiveDialog" :title="archiveDialogMode === 'batch' ? (archiveReviewForm.approved ? `批量归档 ${actionableArchiveRows.length} 条` : `批量退回 ${actionableArchiveRows.length} 条`) : '归档处理'" width="520px">
      <el-form label-position="top">
        <el-form-item v-if="archiveDialogMode === 'single' && attachmentList(currentRow).length" label="表单附件">
          <div style="display: grid; gap: 8px; width: 100%">
            <div
              v-for="item in attachmentList(currentRow)"
              :key="item.id || item.storedName || item.name"
              style="display: flex; justify-content: space-between; align-items: center; gap: 12px"
            >
              <span>{{ item.name || "附件" }}</span>
              <el-button link type="primary" @click="handleDownloadAttachment(item)">下载</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="处理结果"><el-switch v-model="archiveReviewForm.approved" inline-prompt active-text="归档" inactive-text="退回" /></el-form-item>
        <el-form-item label="成绩"><el-input-number v-model="archiveReviewForm.score" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="学院意见"><el-input v-model="archiveReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="archiveDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitArchiveReview">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="evaluationDialog" title="评价确认" width="620px">
      <el-form label-position="top">
        <el-form-item label="学院评分"><el-input-number v-model="evaluationReviewForm.collegeScore" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="学院评语"><el-input v-model="evaluationReviewForm.collegeComment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="evaluationDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitEvaluationReview">确认提交</el-button></template>
    </el-dialog>
  </div>
</template>
