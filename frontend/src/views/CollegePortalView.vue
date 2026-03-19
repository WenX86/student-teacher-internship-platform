<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { get, patch, post } from "../api/http";

const route = useRoute();
const loading = ref(false);
const dashboard = ref({});
const students = ref([]);
const teachers = ref([]);
const mentorApplications = ref([]);
const organizations = ref([]);
const internshipApplications = ref([]);
const forms = ref([]);
const reports = ref({});
const messages = ref([]);
const section = computed(() => route.meta.section || "dashboard");

const studentDialog = ref(false);
const teacherDialog = ref(false);
const organizationDialog = ref(false);
const mentorDialog = ref(false);
const internshipDialog = ref(false);
const archiveDialog = ref(false);
const currentRow = ref(null);

const studentKeyword = ref("");
const teacherKeyword = ref("");
const mentorKeyword = ref("");
const organizationKeyword = ref("");
const internshipKeyword = ref("");
const archiveKeyword = ref("");
const messageKeyword = ref("");

const studentPage = ref(1);
const teacherPage = ref(1);
const mentorPage = ref(1);
const organizationPage = ref(1);
const internshipPage = ref(1);
const archivePage = ref(1);
const messagePage = ref(1);
const pageSize = 5;

const studentForm = reactive({ name: "", studentNo: "", major: "", className: "", phone: "", internshipType: "TEACHING" });
const teacherForm = reactive({ name: "", employeeNo: "", department: "", phone: "" });
const organizationForm = reactive({ name: "", address: "", contactName: "", contactPhone: "", nature: "", cooperationStatus: "合作中" });
const mentorReviewForm = reactive({ approved: true, comment: "" });
const internshipReviewForm = reactive({
  approved: true,
  organizationConfirmation: "已确认接收",
  organizationFeedback: "",
  receivedAt: "",
  comment: "",
});
const archiveReviewForm = reactive({ approved: true, score: 90, comment: "" });

const pendingMentorApplications = computed(() => mentorApplications.value.filter((item) => item.status === "待学院复核"));
const pendingInternshipApplications = computed(() => internshipApplications.value.filter((item) => item.status === "待学院审批"));
const archiveForms = computed(() => forms.value.filter((item) => ["学院审核中", "已归档", "学院退回"].includes(item.status)));
const riskForms = computed(() => forms.value.filter((item) => ["教师退回", "学院退回"].includes(item.status)));

function containsKeyword(values, keyword) {
  return values.filter(Boolean).some((value) => String(value).includes(keyword));
}

const filteredStudents = computed(() => {
  const keyword = studentKeyword.value.trim();
  return keyword ? students.value.filter((item) => containsKeyword([item.name, item.studentNo, item.major, item.className, item.internshipStatus], keyword)) : students.value;
});
const filteredTeachers = computed(() => {
  const keyword = teacherKeyword.value.trim();
  return keyword ? teachers.value.filter((item) => containsKeyword([item.name, item.employeeNo, item.department, item.phone], keyword)) : teachers.value;
});
const filteredMentorApplications = computed(() => {
  const keyword = mentorKeyword.value.trim();
  const base = pendingMentorApplications.value;
  return keyword ? base.filter((item) => containsKeyword([item.student?.name, item.student?.studentNo, item.teacher?.name, item.studentRemark], keyword)) : base;
});
const filteredOrganizations = computed(() => {
  const keyword = organizationKeyword.value.trim();
  return keyword ? organizations.value.filter((item) => containsKeyword([item.name, item.address, item.contactName, item.contactPhone, item.nature, item.cooperationStatus], keyword)) : organizations.value;
});
const filteredInternships = computed(() => {
  const keyword = internshipKeyword.value.trim();
  const base = pendingInternshipApplications.value;
  return keyword ? base.filter((item) => containsKeyword([item.student?.name, item.student?.studentNo, item.organization?.name, item.position, item.status], keyword)) : base;
});
const filteredArchives = computed(() => {
  const keyword = archiveKeyword.value.trim();
  return keyword ? archiveForms.value.filter((item) => containsKeyword([item.studentName, item.studentNo, item.templateName, item.status, item.content?.title, item.content?.summary], keyword)) : archiveForms.value;
});
const filteredMessages = computed(() => {
  const keyword = messageKeyword.value.trim();
  return keyword ? messages.value.filter((item) => containsKeyword([item.type, item.title, item.content], keyword)) : messages.value;
});

function page(list, pageRef) {
  return computed(() => list.value.slice((pageRef.value - 1) * pageSize, pageRef.value * pageSize));
}

const pagedStudents = page(filteredStudents, studentPage);
const pagedTeachers = page(filteredTeachers, teacherPage);
const pagedMentorApplications = page(filteredMentorApplications, mentorPage);
const pagedOrganizations = page(filteredOrganizations, organizationPage);
const pagedInternships = page(filteredInternships, internshipPage);
const pagedArchives = page(filteredArchives, archivePage);
const pagedMessages = page(filteredMessages, messagePage);

async function loadAll() {
  loading.value = true;
  try {
    const [dashboardData, studentData, teacherData, mentorData, organizationData, internshipData, formData, reportData, messageData] =
      await Promise.all([
        get("/dashboard"),
        get("/students"),
        get("/teachers"),
        get("/mentor-applications"),
        get("/organizations"),
        get("/internship-applications"),
        get("/forms"),
        get("/reports/summary"),
        get("/messages"),
      ]);
    dashboard.value = dashboardData;
    students.value = studentData;
    teachers.value = teacherData;
    mentorApplications.value = mentorData;
    organizations.value = organizationData;
    internshipApplications.value = internshipData;
    forms.value = formData;
    reports.value = reportData;
    messages.value = messageData;
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
    ElMessage.success("学生已新增，初始密码为 123456。");
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
    ElMessage.success("教师已新增，初始密码为 123456。");
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
    ElMessage.success("实习单位已新增。");
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
    ElMessage.success(`已重置 ${row.name} 的密码为 123456。`);
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
  internshipReviewForm.organizationConfirmation = row.organizationConfirmation || "已确认接收";
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

function openArchiveReview(row) {
  currentRow.value = row;
  archiveReviewForm.approved = true;
  archiveReviewForm.score = row.score || 90;
  archiveReviewForm.comment = row.collegeComment || "";
  archiveDialog.value = true;
}
async function submitArchiveReview() {
  if (archiveReviewForm.score < 0 || archiveReviewForm.score > 100) {
    ElMessage.warning("归档评分范围应在 0 到 100 之间。");
    return;
  }
  try {
    await post(`/forms/${currentRow.value.id}/college-review`, archiveReviewForm);
    ElMessage.success("归档处理完成。");
    archiveDialog.value = false;
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

watch([studentKeyword, teacherKeyword, mentorKeyword, organizationKeyword, internshipKeyword, archiveKeyword, messageKeyword], () => {
  studentPage.value = 1;
  teacherPage.value = 1;
  mentorPage.value = 1;
  organizationPage.value = 1;
  internshipPage.value = 1;
  archivePage.value = 1;
  messagePage.value = 1;
});

onMounted(loadAll);
watch(() => route.path, loadAll);
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <template v-if="section === 'dashboard'">
      <div class="page-header">
        <div>
          <h2>学院一期看板</h2>
          <div class="subtle">聚合学生、教师、审批、归档和风险学生数量，便于学院管理员每天快速盘点一期进度。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>学生总数</h4><strong>{{ dashboard.studentCount || 0 }}</strong></div>
        <div class="metric-card"><h4>教师总数</h4><strong>{{ dashboard.teacherCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待指导复核</h4><strong>{{ dashboard.pendingMentorReviewCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待实习审批</h4><strong>{{ dashboard.pendingInternshipReviewCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待归档</h4><strong>{{ dashboard.pendingArchiveCount || 0 }}</strong></div>
        <div class="metric-card"><h4>风险学生</h4><strong>{{ dashboard.riskStudentCount || 0 }}</strong></div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header">
            <h2 style="font-size: 20px">待办提醒</h2>
            <el-tag type="warning">{{ pendingMentorApplications.length + pendingInternshipApplications.length + riskForms.length }}</el-tag>
          </div>
          <el-space wrap>
            <el-tag type="warning">待指导复核 {{ pendingMentorApplications.length }}</el-tag>
            <el-tag type="primary">待实习审批 {{ pendingInternshipApplications.length }}</el-tag>
            <el-tag type="danger">风险材料 {{ riskForms.length }}</el-tag>
          </el-space>
        </div>
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">报表摘要</h2></div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="已申请实习">{{ reports.students?.applied || 0 }}</el-descriptions-item>
            <el-descriptions-item label="实习中学生">{{ reports.students?.active || 0 }}</el-descriptions-item>
            <el-descriptions-item label="活跃指导关系">{{ reports.teachers?.activeGuidanceCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="材料归档率">{{ reports.forms?.archiveRate || 0 }}%</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </template>

    <template v-else-if="section === 'students'">
      <div class="page-header">
        <div>
          <h2>学生基础管理</h2>
          <div class="subtle">管理学生基本信息、账号状态、密码重置和实习阶段情况。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="studentDialog = true">新增学生</el-button>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="studentKeyword" placeholder="筛选姓名、学号、专业、班级、状态" clearable style="max-width: 320px" /></div>
        <el-table :data="pagedStudents">
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="studentNo" label="学号" width="130" />
          <el-table-column prop="major" label="专业" min-width="160" />
          <el-table-column prop="className" label="班级" min-width="140" />
          <el-table-column prop="internshipType" label="实习类型" width="120" />
          <el-table-column prop="internshipStatus" label="实习状态" width="120" />
          <el-table-column label="账号状态" width="110">
            <template #default="{ row }"><el-tag :type="row.accountStatus === 'ACTIVE' ? 'success' : 'danger'">{{ row.accountStatus === "ACTIVE" ? "启用" : "停用" }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="toggleStudentStatus(row)">{{ row.accountStatus === "ACTIVE" ? "停用" : "启用" }}</el-button>
              <el-button link type="primary" @click="resetStudentPassword(row)">重置密码</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无学生数据" /></template>
        </el-table>
        <el-pagination v-if="filteredStudents.length > pageSize" v-model:current-page="studentPage" layout="prev, pager, next" :page-size="pageSize" :total="filteredStudents.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
    </template>

    <template v-else-if="section === 'teachers'">
      <div class="page-header">
        <div>
          <h2>教师基础管理</h2>
          <div class="subtle">维护指导教师档案、工号、联系方式和当前带教学生数量。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="teacherDialog = true">新增教师</el-button>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="teacherKeyword" placeholder="筛选姓名、工号、部门、电话" clearable style="max-width: 320px" /></div>
        <el-table :data="pagedTeachers">
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="employeeNo" label="工号" width="130" />
          <el-table-column prop="department" label="教研室" min-width="180" />
          <el-table-column prop="phone" label="联系电话" width="140" />
          <el-table-column prop="studentCount" label="带教学生数" width="120" />
          <template #empty><el-empty description="暂无教师数据" /></template>
        </el-table>
        <el-pagination v-if="filteredTeachers.length > pageSize" v-model:current-page="teacherPage" layout="prev, pager, next" :page-size="pageSize" :total="filteredTeachers.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
    </template>

    <template v-else-if="section === 'mentor'">
      <div class="page-header">
        <div>
          <h2>指导关系复核</h2>
          <div class="subtle">学院对教师已确认的指导申请进行终审，复核通过后指导关系正式生效。</div>
        </div>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="mentorKeyword" placeholder="筛选学生、学号、教师、申请说明" clearable style="max-width: 320px" /></div>
        <el-table :data="pagedMentorApplications">
          <el-table-column label="学生" min-width="160"><template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template></el-table-column>
          <el-table-column label="指导教师" min-width="160"><template #default="{ row }">{{ row.teacher?.name }} / {{ row.teacher?.employeeNo }}</template></el-table-column>
          <el-table-column prop="studentRemark" label="申请说明" min-width="220" />
          <el-table-column prop="teacherRemark" label="教师意见" min-width="180" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openMentorReview(row)">复核</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无待学院复核的指导申请" /></template>
        </el-table>
        <el-pagination v-if="filteredMentorApplications.length > pageSize" v-model:current-page="mentorPage" layout="prev, pager, next" :page-size="pageSize" :total="filteredMentorApplications.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
    </template>

    <template v-else-if="section === 'organizations'">
      <div class="page-header">
        <div>
          <h2>实习单位信息库</h2>
          <div class="subtle">维护实习单位档案、合作状态和联系人信息，供学生发起实习申请时选择。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="organizationDialog = true">新增单位</el-button>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="organizationKeyword" placeholder="筛选单位、地址、联系人、性质、合作状态" clearable style="max-width: 360px" /></div>
        <el-table :data="pagedOrganizations">
          <el-table-column prop="name" label="单位名称" min-width="180" />
          <el-table-column prop="address" label="地址" min-width="220" />
          <el-table-column prop="contactName" label="联系人" width="110" />
          <el-table-column prop="contactPhone" label="联系电话" width="140" />
          <el-table-column prop="nature" label="单位性质" min-width="140" />
          <el-table-column prop="cooperationStatus" label="合作状态" width="120" />
          <template #empty><el-empty description="暂无实习单位数据" /></template>
        </el-table>
        <el-pagination v-if="filteredOrganizations.length > pageSize" v-model:current-page="organizationPage" layout="prev, pager, next" :page-size="pageSize" :total="filteredOrganizations.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
    </template>

    <template v-else-if="section === 'internship'">
      <div class="page-header">
        <div>
          <h2>实习申请审批</h2>
          <div class="subtle">核验指导关系、单位接收情况和时间安排，形成学院审批结论。</div>
        </div>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="internshipKeyword" placeholder="筛选学生、单位、岗位、状态" clearable style="max-width: 320px" /></div>
        <el-table :data="pagedInternships">
          <el-table-column label="学生" min-width="150"><template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template></el-table-column>
          <el-table-column prop="batchName" label="批次" width="150" />
          <el-table-column label="实习单位" min-width="180"><template #default="{ row }">{{ row.organization?.name }}</template></el-table-column>
          <el-table-column prop="position" label="岗位" min-width="150" />
          <el-table-column prop="gradeTarget" label="对象年级" width="120" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="openInternshipReview(row)">审批</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无待审批实习申请" /></template>
        </el-table>
        <el-pagination v-if="filteredInternships.length > pageSize" v-model:current-page="internshipPage" layout="prev, pager, next" :page-size="pageSize" :total="filteredInternships.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
    </template>

    <template v-else-if="section === 'archive'">
      <div class="page-header">
        <div>
          <h2>教师审核与学院归档</h2>
          <div class="subtle">集中处理待归档表单，查看已归档情况，并识别教师退回或学院退回材料。</div>
        </div>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="archiveKeyword" placeholder="筛选学生、表单、标题、状态" clearable style="max-width: 340px" /></div>
        <el-table :data="pagedArchives">
          <el-table-column label="学生" min-width="160"><template #default="{ row }">{{ row.studentName }} / {{ row.studentNo }}</template></el-table-column>
          <el-table-column prop="templateName" label="表单模板" min-width="160" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="标题与摘要" min-width="260"><template #default="{ row }"><div>{{ row.content?.title || "未命名表单" }}</div><div class="subtle">{{ row.content?.summary || "暂无摘要" }}</div></template></el-table-column>
          <el-table-column prop="score" label="评分" width="90" />
          <el-table-column label="操作" width="120"><template #default="{ row }"><el-button v-if="row.status === '学院审核中'" link type="primary" @click="openArchiveReview(row)">归档审核</el-button><span v-else class="subtle">已处理</span></template></el-table-column>
          <template #empty><el-empty description="暂无归档材料" /></template>
        </el-table>
        <el-pagination v-if="filteredArchives.length > pageSize" v-model:current-page="archivePage" layout="prev, pager, next" :page-size="pageSize" :total="filteredArchives.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
    </template>

    <template v-else-if="section === 'reports'">
      <div class="page-header">
        <div>
          <h2>基础统计报表</h2>
          <div class="subtle">围绕一期范围展示学生申请、指导关系和表单归档等基础指标。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>学生总数</h4><strong>{{ reports.students?.total || 0 }}</strong></div>
        <div class="metric-card"><h4>已申请实习</h4><strong>{{ reports.students?.applied || 0 }}</strong></div>
        <div class="metric-card"><h4>实习中</h4><strong>{{ reports.students?.active || 0 }}</strong></div>
        <div class="metric-card"><h4>指导关系生效数</h4><strong>{{ reports.teachers?.activeGuidanceCount || 0 }}</strong></div>
        <div class="metric-card"><h4>表单总数</h4><strong>{{ reports.forms?.total || 0 }}</strong></div>
        <div class="metric-card"><h4>归档率</h4><strong>{{ reports.forms?.archiveRate || 0 }}%</strong></div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <h2 style="font-size: 20px; margin-bottom: 16px">学生进度</h2>
          <el-progress :percentage="reports.students?.total ? Math.round(((reports.students?.active || 0) * 100) / reports.students.total) : 0" />
          <div class="subtle" style="margin-top: 12px">已申请 {{ reports.students?.applied || 0 }}，实习中 {{ reports.students?.active || 0 }}。</div>
        </div>
        <div class="panel-card">
          <h2 style="font-size: 20px; margin-bottom: 16px">材料质量</h2>
          <el-progress :percentage="reports.forms?.archiveRate || 0" status="success" />
          <div class="subtle" style="margin-top: 12px">退回材料 {{ reports.forms?.rejected || 0 }} 份，已归档 {{ reports.forms?.archived || 0 }} 份。</div>
        </div>
      </div>
    </template>

    <template v-else-if="section === 'messages'">
      <div class="page-header">
        <div>
          <h2>消息提醒</h2>
          <div class="subtle">集中查看学院待办、审批结果回执和系统提醒，已读消息可单条消除提醒。</div>
        </div>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="messageKeyword" placeholder="筛选类型、标题、内容" clearable style="max-width: 320px" /></div>
        <el-table :data="pagedMessages">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.read ? 'info' : 'danger'">{{ row.read ? "已读" : "未读" }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="!row.read" link type="primary" @click="markRead(row)">设为已读</el-button><span v-else class="subtle">已处理</span></template></el-table-column>
          <template #empty><el-empty description="暂无消息提醒" /></template>
        </el-table>
        <el-pagination v-if="filteredMessages.length > pageSize" v-model:current-page="messagePage" layout="prev, pager, next" :page-size="pageSize" :total="filteredMessages.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
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
        <el-form-item label="教研室"><el-input v-model="teacherForm.department" /></el-form-item>
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
        <el-form-item label="复核结论"><el-switch v-model="mentorReviewForm.approved" inline-prompt active-text="通过" inactive-text="驳回" /></el-form-item>
        <el-form-item label="复核意见"><el-input v-model="mentorReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="mentorDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitMentorReview">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="internshipDialog" title="实习申请审批" width="560px">
      <el-form label-position="top">
        <el-form-item label="审批结论"><el-switch v-model="internshipReviewForm.approved" inline-prompt active-text="通过" inactive-text="退回" /></el-form-item>
        <el-form-item label="单位确认结果"><el-input v-model="internshipReviewForm.organizationConfirmation" /></el-form-item>
        <el-form-item label="单位反馈"><el-input v-model="internshipReviewForm.organizationFeedback" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="接收日期"><el-date-picker v-model="internshipReviewForm.receivedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="学院意见"><el-input v-model="internshipReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="internshipDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitInternshipReview">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="archiveDialog" title="学院归档审核" width="520px">
      <el-form label-position="top">
        <el-form-item label="归档结论"><el-switch v-model="archiveReviewForm.approved" inline-prompt active-text="归档" inactive-text="退回" /></el-form-item>
        <el-form-item label="评分"><el-input-number v-model="archiveReviewForm.score" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="归档意见"><el-input v-model="archiveReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="archiveDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitArchiveReview">提交</el-button></template>
    </el-dialog>
  </div>
</template>
