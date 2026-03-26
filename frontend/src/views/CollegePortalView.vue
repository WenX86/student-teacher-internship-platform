<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { get, patch, post } from "../api/http";
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";

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

const pageSize = 5;

const studentForm = reactive({ name: "", studentNo: "", major: "", className: "", phone: "", internshipType: "TEACHING" });
const teacherForm = reactive({ name: "", employeeNo: "", department: "", phone: "" });
const organizationForm = reactive({ name: "", address: "", contactName: "", contactPhone: "", nature: "", cooperationStatus: "合作中" });
const mentorReviewForm = reactive({ approved: true, comment: "" });
const internshipReviewForm = reactive({
  approved: true,
  organizationConfirmation: "待登记",
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
  source: messages,
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

async function saveStudent() {
  if (!studentForm.name.trim() || !studentForm.studentNo.trim() || !studentForm.major.trim() || !studentForm.className.trim() || !studentForm.phone.trim()) {
    ElMessage.warning("请完整填写学生基础信息。");
    return;
  }
  try {
    await post("/students", studentForm);
    ElMessage.success("学生已创建，初始密码为 123456。");
    studentDialog.value = false;
    resetStudentForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function saveTeacher() {
  if (!teacherForm.name.trim() || !teacherForm.employeeNo.trim() || !teacherForm.department.trim() || !teacherForm.phone.trim()) {
    ElMessage.warning("请完整填写教师基础信息。");
    return;
  }
  try {
    await post("/teachers", teacherForm);
    ElMessage.success("教师已创建，初始密码为 123456。");
    teacherDialog.value = false;
    resetTeacherForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function saveOrganization() {
  if (!organizationForm.name.trim() || !organizationForm.address.trim() || !organizationForm.contactName.trim() || !organizationForm.contactPhone.trim() || !organizationForm.nature.trim()) {
    ElMessage.warning("请完整填写实习单位信息。");
    return;
  }
  try {
    await post("/organizations", organizationForm);
    ElMessage.success("实习单位已创建。");
    organizationDialog.value = false;
    resetOrganizationForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
async function toggleStudentStatus(row) {
  try {
    await patch(`/students/${row.id}/status`, { status: row.accountStatus === "ACTIVE" ? "DISABLED" : "ACTIVE" });
    ElMessage.success(row.accountStatus === "ACTIVE" ? "学生账号已停用。" : "学生账号已启用。");
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function resetStudentPassword(row) {
  try {
    await post(`/students/${row.id}/reset-password`, {});
    ElMessage.success(`已将 ${row.name} 的密码重置为 123456。`);
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
    await post(`/mentor-applications/${currentRow.value.id}/college-review`, mentorReviewForm);
    ElMessage.success("指导关系复核完成。");
    mentorDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openInternshipReview(row) {
  currentRow.value = row;
  internshipReviewForm.approved = true;
  internshipReviewForm.organizationConfirmation = row.organizationConfirmation || "待登记";
  internshipReviewForm.organizationFeedback = row.organizationFeedback || "";
  internshipReviewForm.receivedAt = row.receivedAt || "";
  internshipReviewForm.comment = row.reviewComment || "";
  internshipDialog.value = true;
}
async function submitInternshipReview() {
  if (!internshipReviewForm.organizationConfirmation.trim()) {
    ElMessage.warning("请填写单位确认结果。");
    return;
  }
  try {
    await post(`/internship-applications/${currentRow.value.id}/review`, internshipReviewForm);
    ElMessage.success("实习申请审批完成。");
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
    ElMessage.warning("请先选择可处理的学院审核中表单。");
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
    ElMessage.warning("当前没有可导出的归档台账。");
    return;
  }
  downloadCsv(
    "archive-ledger.csv",
    [
      { key: "studentName", label: "学生姓名" },
      { key: "studentNo", label: "学号" },
      { key: "templateName", label: "表单模板" },
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
    ElMessage.warning("当前没有可导出的学生统计数据。");
    return;
  }
  downloadCsv(
    "student-report-center.csv",
    [
      { key: "category", label: "分类" },
      { key: "label", label: "项目" },
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
    ElMessage.warning("当前没有可导出的教师工作量数据。");
    return;
  }
  downloadCsv(
    "teacher-workload-report.csv",
    [
      { key: "teacherName", label: "教师姓名" },
      { key: "employeeNo", label: "工号" },
      { key: "department", label: "部门" },
      { key: "studentCount", label: "指导学生数" },
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
    ElMessage.warning("当前没有可导出的模板报表。");
    return;
  }
  downloadCsv(
    "template-ranking-report.csv",
    [
      { key: "templateName", label: "模板名称" },
      { key: "category", label: "类别" },
      { key: "total", label: "总数" },
      { key: "archived", label: "已归档" },
      { key: "pending", label: "待归档" },
      { key: "averageScore", label: "平均成绩" },
    ],
    rows,
  );
}
async function submitArchiveReview() {
  if (archiveReviewForm.score < 0 || archiveReviewForm.score > 100) {
    ElMessage.warning("归档评分范围应在 0 到 100 之间。");
    return;
  }
  try {
    if (archiveDialogMode.value === "batch") {
      if (!actionableArchiveRows.value.length) {
        ElMessage.warning("当前没有可批量处理的表单。");
        return;
      }
      const result = await post("/forms/batch-college-review", {
        formIds: actionableArchiveRows.value.map((item) => item.id),
        approved: archiveReviewForm.approved,
        score: archiveReviewForm.score,
        comment: archiveReviewForm.comment,
      });
      const skippedCount = result?.skipped?.length || 0;
      ElMessage.success(`批量处理完成，成功 ${result?.processedCount || 0} 条${skippedCount ? `，跳过 ${skippedCount} 条` : ""}。`);
      archiveSelection.value = [];
    } else {
      await post(`/forms/${currentRow.value.id}/college-review`, archiveReviewForm);
      ElMessage.success("归档处理完成。");
    }
    archiveDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openEvaluationReview(row) {
  currentRow.value = row;
  evaluationReviewForm.collegeScore = row.collegeScore ?? row.recommendedScore ?? row.finalScore ?? 90;
  evaluationReviewForm.collegeComment = row.collegeComment || "";
  evaluationDialog.value = true;
}

async function submitEvaluationReview() {
  if (evaluationReviewForm.collegeScore < 0 || evaluationReviewForm.collegeScore > 100) {
    ElMessage.warning("学院确认成绩需在 0 到 100 之间。");
    return;
  }
  try {
    await post(`/evaluations/${currentRow.value.id}/college-confirm`, evaluationReviewForm);
    ElMessage.success("评价确认完成。");
    evaluationDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function sendReminder(row) {
  try {
    await post(`/risk-alerts/${row.id}/remind`, {});
    ElMessage.success(row.remindActionLabel ? `${row.remindActionLabel}已发送。` : "催办提醒已发送。");
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
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <template v-if="section === 'dashboard'">
      <div class="page-header">
        <div>
          <h2>学院一站式看板</h2>
          <div class="subtle">集中查看学生、教师、审批、归档与风险概况，支持学院管理员统一处理。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>学生总数</h4><strong>{{ dashboard.studentCount || 0 }}</strong></div>
        <div class="metric-card"><h4>教师总数</h4><strong>{{ dashboard.teacherCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待复核指导关系</h4><strong>{{ dashboard.pendingMentorReviewCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待审批实习申请</h4><strong>{{ dashboard.pendingInternshipReviewCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待归档表单</h4><strong>{{ dashboard.pendingArchiveCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待确认评价</h4><strong>{{ pendingEvaluationRecords.length }}</strong></div>
        <div class="metric-card"><h4>风险学生</h4><strong>{{ dashboard.riskStudentCount || 0 }}</strong></div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header">
            <h2 style="font-size: 20px">待办汇总</h2>
            <el-tag type="warning">{{ pendingMentorApplications.length + pendingInternshipApplications.length + riskForms.length }}</el-tag>
          </div>
          <el-space wrap>
            <el-tag type="warning">待复核指导关系 {{ pendingMentorApplications.length }}</el-tag>
            <el-tag type="primary">待审批实习申请 {{ pendingInternshipApplications.length }}</el-tag>
            <el-tag type="danger">退回风险材料 {{ riskForms.length }}</el-tag>
          </el-space>
        </div>
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">一期摘要</h2></div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="已申请实习">{{ reports.students?.applied || 0 }}</el-descriptions-item>
            <el-descriptions-item label="实习中学生">{{ reports.students?.active || 0 }}</el-descriptions-item>
            <el-descriptions-item label="有效指导关系">{{ reports.teachers?.activeGuidanceCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="整体归档率">{{ reports.forms?.archiveRate || 0 }}%</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </template>

    <template v-else-if="section === 'students'">
      <div class="page-header">
        <div>
          <h2>学生基础管理</h2>
          <div class="subtle">维护学生基础信息、账号状态和实习类型，支持密码重置。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="studentDialog = true">新增学生</el-button>
      </div>
      <FilterTablePanel v-model:keyword="studentKeyword" v-model:current-page="studentPage" placeholder="筛选姓名、学号、专业、班级、状态" :total="filteredStudents.length" :page-size="pageSize">
        <el-table :data="pagedStudents" style="margin-top: 16px">
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="studentNo" label="学号" width="130" />
          <el-table-column prop="major" label="专业" min-width="160" />
          <el-table-column prop="className" label="班级" min-width="140" />
          <el-table-column prop="internshipType" label="实习类型" width="120" />
          <el-table-column prop="internshipStatus" label="实习状态" width="120" />
          <el-table-column label="账号状态" width="110"><template #default="{ row }"><el-tag :type="row.accountStatus === 'ACTIVE' ? 'success' : 'danger'">{{ row.accountStatus === "ACTIVE" ? "启用" : "停用" }}</el-tag></template></el-table-column>
          <el-table-column label="操作" min-width="180"><template #default="{ row }"><el-button link type="primary" @click="toggleStudentStatus(row)">{{ row.accountStatus === "ACTIVE" ? "停用" : "启用" }}</el-button><el-button link type="primary" @click="resetStudentPassword(row)">重置密码</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无学生数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'teachers'">
      <div class="page-header">
        <div>
          <h2>教师基础管理</h2>
          <div class="subtle">维护指导教师信息，配合指导关系和工作量统计。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="teacherDialog = true">新增教师</el-button>
      </div>
      <FilterTablePanel v-model:keyword="teacherKeyword" v-model:current-page="teacherPage" placeholder="筛选姓名、工号、部门、电话" :total="filteredTeachers.length" :page-size="pageSize">
        <el-table :data="pagedTeachers" style="margin-top: 16px">
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="employeeNo" label="工号" width="130" />
          <el-table-column prop="department" label="部门" min-width="180" />
          <el-table-column prop="phone" label="联系电话" width="140" />
          <el-table-column prop="studentCount" label="指导学生数" width="120" />
          <template #empty><el-empty description="暂无教师数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'mentor'">
      <div class="page-header">
        <div>
          <h2>指导关系复核</h2>
          <div class="subtle">学院对教师已确认的申请做最终复核，通过后指导关系正式生效。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="mentorKeyword" v-model:current-page="mentorPage" placeholder="筛选学生、教师、申请说明" :total="filteredMentorApplications.length" :page-size="pageSize">
        <el-table :data="pagedMentorApplications" style="margin-top: 16px">
          <el-table-column label="学生" min-width="160"><template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template></el-table-column>
          <el-table-column label="指导教师" min-width="160"><template #default="{ row }">{{ row.teacher?.name }} / {{ row.teacher?.employeeNo }}</template></el-table-column>
          <el-table-column prop="studentRemark" label="申请说明" min-width="220" />
          <el-table-column prop="teacherRemark" label="教师意见" min-width="180" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openMentorReview(row)">复核</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无待学院复核的指导关系" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'organizations'">
      <div class="page-header">
        <div>
          <h2>实习单位信息库</h2>
          <div class="subtle">维护实习单位基础信息、合作状态和联系人信息，供学生申请时选择。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="organizationDialog = true">新增单位</el-button>
      </div>
      <FilterTablePanel v-model:keyword="organizationKeyword" v-model:current-page="organizationPage" placeholder="筛选单位、地址、联系人、状态" :total="filteredOrganizations.length" :page-size="pageSize">
        <el-table :data="pagedOrganizations" style="margin-top: 16px">
          <el-table-column prop="name" label="单位名称" min-width="180" />
          <el-table-column prop="address" label="地址" min-width="220" />
          <el-table-column prop="contactName" label="联系人" width="110" />
          <el-table-column prop="contactPhone" label="联系电话" width="140" />
          <el-table-column prop="nature" label="单位性质" min-width="140" />
          <el-table-column prop="cooperationStatus" label="合作状态" width="120" />
          <template #empty><el-empty description="暂无实习单位数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'internship'">
      <div class="page-header">
        <div>
          <h2>实习申请审批</h2>
          <div class="subtle">学院审核学生实习申请，并登记单位确认结果与审批意见。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="internshipKeyword" v-model:current-page="internshipPage" placeholder="筛选学生、单位、岗位、状态" :total="filteredInternships.length" :page-size="pageSize">
        <el-table :data="pagedInternships" style="margin-top: 16px">
          <el-table-column label="学生" min-width="150"><template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template></el-table-column>
          <el-table-column prop="batchName" label="批次" width="150" />
          <el-table-column label="实习单位" min-width="180"><template #default="{ row }">{{ row.organization?.name }}</template></el-table-column>
          <el-table-column prop="position" label="岗位" min-width="150" />
          <el-table-column prop="gradeTarget" label="年级" width="120" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openInternshipReview(row)">审批</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无待学院审批的实习申请" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'archive'">
      <div class="page-header">
        <div>
          <h2>学院归档中心</h2>
          <div class="subtle">统一处理学院审核中的表单，支持批量归档、批量退回和归档台账导出。</div>
        </div>
        <div style="display: flex; gap: 12px; flex-wrap: wrap">
          <el-button plain @click="exportArchiveLedger">导出归档台账</el-button>
          <el-button type="warning" plain :disabled="!actionableArchiveRows.length" @click="openBatchArchiveReview(false)">批量退回</el-button>
          <el-button type="primary" color="#0f766e" :disabled="!actionableArchiveRows.length" @click="openBatchArchiveReview(true)">批量归档</el-button>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="archiveKeyword" v-model:current-page="archivePage" placeholder="筛选学生、表单、标题、状态" :total="filteredArchives.length" :page-size="pageSize">
        <div class="subtle" style="margin-bottom: 12px">已选 {{ actionableArchiveRows.length }} 条可处理记录，仅学院审核中的表单支持批量操作。</div>
        <el-table :data="pagedArchives" style="margin-top: 16px" @selection-change="handleArchiveSelectionChange">
          <el-table-column type="selection" width="50" :selectable="(row) => row.status === '学院审核中'" />
          <el-table-column label="学生" min-width="160"><template #default="{ row }">{{ row.studentName }} / {{ row.studentNo }}</template></el-table-column>
          <el-table-column prop="templateName" label="表单模板" min-width="160" />
          <el-table-column prop="mentorTeacherName" label="指导教师" min-width="120" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="内容摘要" min-width="260"><template #default="{ row }"><div>{{ row.content?.title || "未填写标题" }}</div><div class="subtle">{{ row.content?.summary || "暂无摘要" }}</div></template></el-table-column>
          <el-table-column prop="score" label="成绩" width="90" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status === '学院审核中'" link type="primary" @click="openArchiveReview(row)">归档处理</el-button><span v-else class="subtle">无需处理</span></template></el-table-column>
          <template #empty><el-empty description="暂无归档数据" /></template>
        </el-table>
      </FilterTablePanel>
    </template>
    <template v-else-if="section === 'evaluations'">
      <div class="page-header">
        <div>
          <h2>评价汇总确认</h2>
          <div class="subtle">学院确认教师提交的多维评价结果，并补录最终成绩和学院意见。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="evaluationKeyword" v-model:current-page="evaluationPage" placeholder="筛选学生、教师、评价内容、状态" :total="filteredEvaluations.length" :page-size="pageSize">
        <el-table :data="pagedEvaluations" style="margin-top: 16px">
          <el-table-column label="学生" min-width="160"><template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template></el-table-column>
          <el-table-column label="指导教师" width="130"><template #default="{ row }">{{ row.teacher?.name }}</template></el-table-column>
          <el-table-column label="评价维度" min-width="260"><template #default="{ row }"><el-space wrap><el-tag v-for="item in row.dimensionScores || []" :key="`${row.id}-${item.key}`" type="success">{{ item.label }} {{ item.score }}</el-tag></el-space></template></el-table-column>
          <el-table-column prop="recommendedScore" label="建议成绩" width="100" />
          <el-table-column prop="finalScore" label="最终成绩" width="100" />
          <el-table-column label="学院状态" width="100"><template #default="{ row }"><el-tag :type="row.confirmedByCollege ? 'success' : 'warning'">{{ row.confirmedByCollege ? "已确认" : "待确认" }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openEvaluationReview(row)">{{ row.confirmedByCollege ? "重新确认" : "确认结果" }}</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无评价记录" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'reports'">
      <div class="page-header">
        <div>
          <h2>报表中心</h2>
          <div class="subtle">围绕学生状态、表单归档、教师工作量、评价质量和单位使用情况进行统计分析。</div>
        </div>
        <div style="display: flex; gap: 12px; flex-wrap: wrap">
          <el-button plain @click="exportStudentReport">导出学生报表</el-button>
          <el-button plain @click="exportTeacherReport">导出教师报表</el-button>
          <el-button type="primary" plain color="#0f766e" @click="exportTemplateReport">导出模板报表</el-button>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>学生总数</h4><strong>{{ reportCenter.overview?.studentCount || 0 }}</strong></div>
        <div class="metric-card"><h4>教师总数</h4><strong>{{ reportCenter.overview?.teacherCount || 0 }}</strong></div>
        <div class="metric-card"><h4>实习单位数</h4><strong>{{ reportCenter.overview?.organizationCount || 0 }}</strong></div>
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
            <template #empty><el-empty description="暂无学生状态数据" /></template>
          </el-table>
        </div>
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">表单状态分布</h2><el-tag type="success">归档率 {{ reportCenter.overview?.archiveRate || 0 }}%</el-tag></div>
          <el-table :data="reportCenter.forms?.statusDistribution || []" style="margin-top: 16px">
            <el-table-column prop="label" label="状态" />
            <el-table-column prop="count" label="数量" width="100" />
            <template #empty><el-empty description="暂无表单状态数据" /></template>
          </el-table>
        </div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">模板排名</h2><el-tag type="warning">{{ reportCenter.forms?.templateRanking?.length || 0 }} 个模板</el-tag></div>
          <el-table :data="reportCenter.forms?.templateRanking || []" style="margin-top: 16px">
            <el-table-column prop="templateName" label="模板" min-width="160" />
            <el-table-column prop="category" label="类别" width="110" />
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
            <el-table-column prop="teacherName" label="教师" min-width="140" />
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
          <div class="page-header"><h2 style="font-size: 20px">评价质量汇总</h2><el-tag type="success">待确认 {{ reportCenter.evaluations?.summary?.pending || 0 }}</el-tag></div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="评价总数">{{ reportCenter.evaluations?.summary?.total || 0 }}</el-descriptions-item>
            <el-descriptions-item label="学院已确认">{{ reportCenter.evaluations?.summary?.confirmed || 0 }}</el-descriptions-item>
            <el-descriptions-item label="待确认">{{ reportCenter.evaluations?.summary?.pending || 0 }}</el-descriptions-item>
            <el-descriptions-item label="平均成绩">{{ reportCenter.evaluations?.summary?.averageScore || 0 }}</el-descriptions-item>
          </el-descriptions>
          <el-space wrap style="margin-top: 16px">
            <el-tag v-for="item in reportCenter.evaluations?.scoreDistribution || []" :key="item.label" type="success">{{ item.label }}：{{ item.count }}</el-tag>
          </el-space>
        </div>
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">单位使用情况</h2><el-tag>{{ reportCenter.organizations?.usageRanking?.length || 0 }} 个</el-tag></div>
          <el-table :data="reportCenter.organizations?.usageRanking || []" style="margin-top: 16px">
            <el-table-column prop="organizationName" label="实习单位" min-width="180" />
            <el-table-column prop="cooperationStatus" label="合作状态" min-width="120" />
            <el-table-column prop="count" label="使用次数" width="100" />
            <template #empty><el-empty description="暂无单位使用数据" /></template>
          </el-table>
        </div>
      </div>
      <div class="panel-card">
        <div class="page-header"><h2 style="font-size: 20px">近六个月趋势</h2><el-tag type="warning">提交 / 归档 / 评价</el-tag></div>
        <el-table :data="reportCenter.trends || []" style="margin-top: 16px">
          <el-table-column prop="month" label="月份" width="120" />
          <el-table-column prop="submitted" label="提交数" width="120" />
          <el-table-column prop="archived" label="归档数" width="120" />
          <el-table-column prop="evaluated" label="评价数" width="120" />
          <template #empty><el-empty description="暂无趋势数据" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'alerts'">
      <div class="page-header">
        <div>
          <h2>预警催办</h2>
          <div class="subtle">识别超时审批、退回未改和待确认评价等风险事项，并支持一键催办。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="alertKeyword" v-model:current-page="alertPage" placeholder="筛选类型、标题、级别、对象" :total="filteredAlerts.length" :page-size="pageSize">
        <el-table :data="pagedAlerts" style="margin-top: 16px">
          <el-table-column prop="category" label="类型" width="120" />
          <el-table-column label="级别" width="100"><template #default="{ row }"><el-tag :type="row.level === 'danger' ? 'danger' : 'warning'">{{ row.level === 'danger' ? '高' : '中' }}</el-tag></template></el-table-column>
          <el-table-column prop="title" label="预警标题" min-width="220" />
          <el-table-column prop="content" label="说明" min-width="260" />
          <el-table-column prop="targetName" label="催办对象" width="120" />
          <el-table-column prop="overdueDays" label="超时天数" width="100" />
          <el-table-column label="操作" width="140"><template #default="{ row }"><el-button v-if="row.remindable" link type="primary" @click="sendReminder(row)">{{ row.remindActionLabel || "发送催办" }}</el-button><span v-else class="subtle">请尽快处理</span></template></el-table-column>
          <template #empty><el-empty description="暂无风险预警" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'messages'">
      <div class="page-header">
        <div>
          <h2>消息中心</h2>
          <div class="subtle">集中查看学院待办、催办提醒和系统结果反馈。</div>
        </div>
      </div>
      <FilterTablePanel v-model:keyword="messageKeyword" v-model:current-page="messagePage" placeholder="筛选类型、标题、内容" :total="filteredMessages.length" :page-size="pageSize">
        <el-table :data="pagedMessages" style="margin-top: 16px">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.read ? 'info' : 'danger'">{{ row.read ? "已读" : "未读" }}</el-tag></template></el-table-column>
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
        <el-form-item label="实习类型"><el-select v-model="studentForm.internshipType" style="width: 100%"><el-option label="任课实习" value="TEACHING" /><el-option label="班主任实习" value="HEAD_TEACHER" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="studentDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="saveStudent">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="teacherDialog" title="新增教师" width="520px">
      <el-form label-position="top">
        <el-form-item label="姓名"><el-input v-model="teacherForm.name" /></el-form-item>
        <el-form-item label="工号"><el-input v-model="teacherForm.employeeNo" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="teacherForm.department" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="teacherForm.phone" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="teacherDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="saveTeacher">提交</el-button></template>
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
      <template #footer><el-button @click="organizationDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="saveOrganization">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="mentorDialog" title="指导关系复核" width="520px">
      <el-form label-position="top">
        <el-form-item label="审核结论"><el-switch v-model="mentorReviewForm.approved" inline-prompt active-text="通过" inactive-text="驳回" /></el-form-item>
        <el-form-item label="复核意见"><el-input v-model="mentorReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="mentorDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitMentorReview">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="internshipDialog" title="实习申请审批" width="560px">
      <el-form label-position="top">
        <el-form-item label="审批结论"><el-switch v-model="internshipReviewForm.approved" inline-prompt active-text="通过" inactive-text="退回" /></el-form-item>
        <el-form-item label="单位确认结果"><el-input v-model="internshipReviewForm.organizationConfirmation" /></el-form-item>
        <el-form-item label="单位反馈"><el-input v-model="internshipReviewForm.organizationFeedback" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="单位确认时间"><el-date-picker v-model="internshipReviewForm.receivedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="学院意见"><el-input v-model="internshipReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="internshipDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitInternshipReview">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="archiveDialog" :title="archiveDialogMode === 'batch' ? (archiveReviewForm.approved ? `批量归档 ${actionableArchiveRows.length} 条` : `批量退回 ${actionableArchiveRows.length} 条`) : '学院归档处理'" width="520px">
      <el-form label-position="top">
        <el-form-item label="处理方式"><el-switch v-model="archiveReviewForm.approved" inline-prompt active-text="归档" inactive-text="退回" /></el-form-item>
        <el-form-item label="成绩"><el-input-number v-model="archiveReviewForm.score" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="归档意见"><el-input v-model="archiveReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="archiveDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitArchiveReview">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="evaluationDialog" title="确认评价结果" width="620px">
      <el-form label-position="top">
        <el-form-item label="学院确认成绩"><el-input-number v-model="evaluationReviewForm.collegeScore" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="学院确认意见"><el-input v-model="evaluationReviewForm.collegeComment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="evaluationDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitEvaluationReview">确认提交</el-button></template>
    </el-dialog>
  </div>
</template>
