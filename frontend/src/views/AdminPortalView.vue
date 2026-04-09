<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { ElMessageBox } from "element-plus";
import { get, patch, post, put } from "../api/http";
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";

const route = useRoute();
const loading = ref(false);
const dashboard = ref({});
const collegeApplications = ref([]);
const collegeAdmins = ref([]);
const basicData = ref({});
const logs = ref([]);
const formTemplates = ref([]);
const systemSettings = ref([]);
const section = computed(() => route.meta.section || "dashboard");

const reviewDialog = ref(false);
const reviewResultDialog = ref(false);
const templateDialog = ref(false);
const collegeDialog = ref(false);
const collegeAdminDialog = ref(false);
const settingsSaving = ref(false);
const templateMode = ref("create");
const currentRow = ref(null);
const reviewForm = reactive({ approved: true, comment: "" });
const reviewResult = ref(null);
const templateForm = reactive(createEmptyTemplateForm());
const collegeForm = reactive({
  name: "",
  contactName: "",
  contactPhone: "",
  description: "",
});
const collegeAdminForm = reactive({
  collegeId: "",
  name: "",
  account: "",
});
const settingsForm = reactive({});

const pageSize = 6;
const applicationStatus = ref("ALL");
const applicationStatusOptions = [
  { label: "全部状态", value: "ALL" },
  { label: "只看待开通", value: "待审核" },
  { label: "只看已开通", value: "已通过" },
  { label: "只看已驳回", value: "已驳回" },
];
const applicationSource = computed(() => {
  if (applicationStatus.value === "ALL") {
    return collegeApplications.value;
  }
  return collegeApplications.value.filter((item) => item.status === applicationStatus.value);
});

function getCollegeRecordStatusMeta(status) {
  if (status === "待审核") {
    return { label: "待开通", type: "warning" };
  }
  if (status === "已通过") {
    return { label: "已开通", type: "success" };
  }
  if (status === "已驳回") {
    return { label: "已驳回", type: "danger" };
  }
  return { label: status || "-", type: "info" };
}

const {
  keyword: applicationKeyword,
  currentPage: applicationPage,
  filteredItems: filteredCollegeApplications,
  pagedItems: pagedCollegeApplications,
} = useFilteredPagination({
  source: applicationSource,
  matcher: (item) => [item.collegeName, item.contactName, item.contactPhone, item.status],
  pageSize,
});

const {
  keyword: templateKeyword,
  currentPage: templatePage,
  filteredItems: filteredTemplates,
  pagedItems: pagedTemplates,
} = useFilteredPagination({
  source: formTemplates,
  matcher: (item) => [item.code, item.name, item.category, item.description, ...(item.applicableTypes || [])],
  pageSize,
});

const {
  keyword: logKeyword,
  currentPage: logPage,
  filteredItems: filteredLogs,
  pagedItems: pagedLogs,
} = useFilteredPagination({
  source: logs,
  matcher: (item) => [item.type, item.action, item.detail, item.operatorId],
  pageSize,
});

const enabledTemplateCount = computed(() => formTemplates.value.filter((item) => item.enabled).length);
const disabledTemplateCount = computed(() => formTemplates.value.filter((item) => !item.enabled).length);
const reminderSettings = computed(() => systemSettings.value.filter((item) => item.category === "REMINDER"));
const enabledReminderCount = computed(() => reminderSettings.value.filter((item) => item.valueType === "BOOLEAN" && settingsForm[item.key] === true).length);
const availableCollegesForAdmin = computed(() => {
  const existingCollegeIds = new Set(collegeAdmins.value.map((item) => item.collegeId).filter(Boolean));
  return (basicData.value.colleges || []).filter((item) => !existingCollegeIds.has(item.id));
});

const collegeTotalCount = computed(() => basicData.value.colleges?.length || 0);
const missingCollegeCount = computed(() => Math.max(collegeTotalCount.value - collegeAdmins.value.length, 0));
const incompleteCollegeCount = computed(() => (basicData.value.colleges || []).filter((item) => !item.contactName || !item.contactPhone).length);
const activeCollegeAdminCount = computed(() => collegeAdmins.value.filter((item) => item.status === "ACTIVE").length);
const disabledCollegeAdminCount = computed(() => collegeAdmins.value.filter((item) => item.status !== "ACTIVE").length);
const dashboardTodoItems = computed(() => [
  {
    label: "待录入学院",
    value: missingCollegeCount.value,
    hint: "学院已建但尚未创建管理员账号。",
  },
  {
    label: "待完善学院信息",
    value: incompleteCollegeCount.value,
    hint: "联系人或联系电话缺失，建议补齐。",
  },
  {
    label: "待处理消息",
    value: dashboard.value.unreadMessages || 0,
    hint: "消息中心中尚未查看的提醒。",
  },
  {
    label: "停用账号",
    value: disabledCollegeAdminCount.value,
    hint: "处于停用状态的学院管理员账号。",
  },
]);
const recentActivities = computed(() =>
  [...logs.value]
    .slice()
    .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())
    .slice(0, 5)
    .map((item) => ({
      title: item.action || item.type || "系统动态",
      meta: [item.detail, item.operatorId].filter(Boolean).join(" · ") || "系统记录",
      time: formatShortTime(item.createdAt),
    }))
);
const collegeStatusSegments = computed(() => {
  const segments = [
    { label: "已录入学院", count: collegeAdmins.value.length, color: "#0f766e" },
    { label: "待录入学院", count: missingCollegeCount.value, color: "#f59e0b" },
  ];
  const total = segments.reduce((sum, item) => sum + item.count, 0);
  return segments.map((item) => ({
    ...item,
    percent: total > 0 ? (item.count / total) * 100 : 0,
  }));
});

const collegeCoverageRate = computed(() => {
  if (!collegeTotalCount.value) {
    return 0;
  }
  return Math.round((collegeAdmins.value.length / collegeTotalCount.value) * 100);
});
const pendingCollegePreview = computed(() =>
  (basicData.value.colleges || [])
    .filter((item) => !collegeAdmins.value.some((admin) => admin.collegeId === item.id))
    .slice(0, 3)
    .map((item) => ({
      name: item.name,
      contact: item.contactName || '未填写联系人',
      phone: item.contactPhone || '未填写电话',
    }))
);
function formatShortTime(value) {
  if (!value) {
    return "-";
  }
  const text = String(value).replace("T", " ").replace(/\.\d+$/, "");
  return text.length > 19 ? text.slice(0, 19) : text;
}

function createDefaultFields() {
  return [
    { key: "title", label: "标题", type: "text", required: true, placeholder: "请输入标题" },
    { key: "summary", label: "内容摘要", type: "textarea", required: true, placeholder: "请输入内容摘要" },
  ];
}

function createEmptyTemplateForm() {
  return {
    code: "",
    name: "",
    category: "COMMON",
    description: "",
    applicableTypes: ["TEACHING", "HEAD_TEACHER"],
    enabled: true,
    sortNo: 100,
    fieldSchema: createDefaultFields(),
  };
}

function resetTemplateForm() {
  const next = createEmptyTemplateForm();
  templateForm.code = next.code;
  templateForm.name = next.name;
  templateForm.category = next.category;
  templateForm.description = next.description;
  templateForm.applicableTypes = [...next.applicableTypes];
  templateForm.enabled = next.enabled;
  templateForm.sortNo = next.sortNo;
  templateForm.fieldSchema = next.fieldSchema.map((item) => ({ ...item }));
}

function syncSettingsForm() {
  for (const item of systemSettings.value) {
    if (item.valueType === "BOOLEAN") {
      settingsForm[item.key] = String(item.value ?? "0") === "1";
    } else if (item.valueType === "INTEGER") {
      settingsForm[item.key] = Number(item.value ?? 0);
    } else {
      settingsForm[item.key] = String(item.value ?? "");
    }
  }
}
async function loadAll() {
  loading.value = true;
  try {
    const [dashboardData, applicationData, basicDataResult, collegeAdminData, settingData, templateData, logData] = await Promise.all([
      get("/dashboard"),
      get("/admin/college-applications"),
      get("/admin/basic-data"),
      get("/admin/college-admins"),
      get("/admin/system-settings"),
      get("/admin/form-templates"),
      get("/admin/logs"),
    ]);
    dashboard.value = dashboardData;
    collegeApplications.value = applicationData;
    basicData.value = basicDataResult;
    collegeAdmins.value = collegeAdminData;
    systemSettings.value = settingData;
    syncSettingsForm();
    formTemplates.value = templateData;
    logs.value = logData;
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}

function openReview(row) {
  currentRow.value = row;
  reviewForm.approved = true;
  reviewForm.comment = row.reviewComment || "";
  reviewResult.value = null;
  reviewDialog.value = true;
}

async function submitReview() {
  try {
    reviewResult.value = await post(`/admin/college-applications/${currentRow.value.id}/review`, reviewForm);
    applicationStatus.value = reviewResult.value?.status || "ALL";
    ElMessage.success(`学院账号开通记录已处理，状态已更新为${getCollegeRecordStatusMeta(reviewResult.value?.status || "").label || "最新状态"}。`);
    reviewDialog.value = false;
    await loadAll();
    reviewResultDialog.value = true;
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function findCollegeAdminForApplication(row) {
  return collegeAdmins.value.find((item) => item.collegeName === row.collegeName) || null;
}

function reviewCreatedNewCollegeAdmin(row) {
  return String(row.reviewComment || "").includes("初始密码：123456");
}

function reviewHasCollegeAdmin(row) {
  return String(row.reviewComment || "").includes("学院管理员账号：");
}

function resetCollegeForm() {
  collegeForm.name = "";
  collegeForm.contactName = "";
  collegeForm.contactPhone = "";
  collegeForm.description = "";
}

function openCreateCollege() {
  resetCollegeForm();
  collegeDialog.value = true;
}

function openCreateTemplate() {
  templateMode.value = "create";
  resetTemplateForm();
  templateDialog.value = true;
}

function resetCollegeAdminForm() {
  collegeAdminForm.collegeId = "";
  collegeAdminForm.name = "";
  collegeAdminForm.account = "";
}

function openCreateCollegeAdmin() {
  if (!availableCollegesForAdmin.value.length) {
    ElMessage.warning("当前学院都已存在管理员账号；如果是新学院，请先新增学院基础信息后再创建学院管理员账号。");
    return;
  }
  resetCollegeAdminForm();
  collegeAdminForm.collegeId = availableCollegesForAdmin.value[0].id;
  collegeAdminDialog.value = true;
}

function openEditTemplate(row) {
  templateMode.value = "edit";
  templateForm.code = row.code;
  templateForm.name = row.name;
  templateForm.category = row.category;
  templateForm.description = row.description || "";
  templateForm.applicableTypes = Array.isArray(row.applicableTypes) ? [...row.applicableTypes] : [];
  templateForm.enabled = row.enabled !== false;
  templateForm.sortNo = row.sortNo ?? 100;
  templateForm.fieldSchema = (row.fieldSchema?.length ? row.fieldSchema : createDefaultFields()).map((item) => ({ ...item }));
  templateDialog.value = true;
}

function addTemplateField() {
  templateForm.fieldSchema.push({
    key: "",
    label: "",
    type: "text",
    required: false,
    placeholder: "",
  });
}

function removeTemplateField(index) {
  if (templateForm.fieldSchema.length <= 1) {
    ElMessage.warning("至少保留一个表单字段。");
    return;
  }
  templateForm.fieldSchema.splice(index, 1);
}

function validateTemplateForm() {
  if (!templateForm.code.trim() && templateMode.value === "create") {
    ElMessage.warning("请输入模板编码。");
    return false;
  }
  if (!templateForm.name.trim()) {
    ElMessage.warning("请输入模板名称。");
    return false;
  }
  if (!templateForm.applicableTypes.length) {
    ElMessage.warning("请至少选择一个适用实习类型。");
    return false;
  }

  const keySet = new Set();
  for (const field of templateForm.fieldSchema) {
    if (!field.key.trim() || !field.label.trim()) {
      ElMessage.warning("请完整填写字段编码和字段名称。");
      return false;
    }
    if (!/^[a-zA-Z][a-zA-Z0-9_]{1,31}$/.test(field.key.trim())) {
      ElMessage.warning("字段编码需以字母开头，仅支持字母、数字和下划线。");
      return false;
    }
    if (keySet.has(field.key.trim())) {
      ElMessage.warning("字段编码不能重复。");
      return false;
    }
    keySet.add(field.key.trim());
  }
  return true;
}

function buildTemplatePayload() {
  return {
    code: templateForm.code.trim(),
    name: templateForm.name.trim(),
    category: templateForm.category,
    description: templateForm.description.trim(),
    applicableTypes: [...templateForm.applicableTypes],
    enabled: templateForm.enabled,
    sortNo: templateForm.sortNo ?? 100,
    fieldSchema: templateForm.fieldSchema.map((item) => ({
      key: item.key.trim(),
      label: item.label.trim(),
      type: item.type,
      required: item.required === true,
      placeholder: (item.placeholder || "").trim(),
    })),
  };
}

async function saveSystemSettings() {
  const items = reminderSettings.value.map((item) => {
    const rawValue = settingsForm[item.key];
    if (item.valueType === "BOOLEAN") {
      return { key: item.key, value: rawValue ? "1" : "0" };
    }
    if (item.valueType === "INTEGER") {
      return { key: item.key, value: String(rawValue ?? 0).trim() };
    }
    return { key: item.key, value: String(rawValue ?? "").trim() };
  });
  settingsSaving.value = true;
  try {
    await put("/admin/system-settings", { items });
    ElMessage.success("系统参数已保存。");
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    settingsSaving.value = false;
  }
}
async function submitTemplate() {
  if (!validateTemplateForm()) {
    return;
  }

  const payload = buildTemplatePayload();

  try {
    if (templateMode.value === "create") {
      await post("/admin/form-templates", payload);
      ElMessage.success("表单模板已创建。");
    } else {
      await put(`/admin/form-templates/${templateForm.code}`, payload);
      ElMessage.success("表单模板已更新。");
    }
    templateDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function toggleTemplateStatus(row) {
  try {
    await patch(`/admin/form-templates/${row.code}/status`, { enabled: !row.enabled });
    ElMessage.success(row.enabled ? "表单模板已停用。" : "表单模板已启用。");
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function toggleCollegeAdminStatus(row) {
  try {
    await patch(`/admin/college-admins/${row.id}/status`, { status: row.status === "ACTIVE" ? "DISABLED" : "ACTIVE" });
    ElMessage.success(row.status === "ACTIVE" ? "学院管理员账号已停用。" : "学院管理员账号已启用。");
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function resetCollegeAdminPassword(row) {
  try {
    await ElMessageBox.confirm(`确认将 ${row.name} 的密码重置为 123456 吗？`, "重置学院管理员密码", {
      confirmButtonText: "确认重置",
      cancelButtonText: "取消",
      type: "warning",
    });
    await post(`/admin/college-admins/${row.id}/reset-password`, {});
    ElMessage.success("学院管理员密码已重置为 123456，并要求下次登录修改密码。");
    await loadAll();
  } catch (error) {
    if (error === "cancel") {
      return;
    }
    ElMessage.error(error.message || "重置密码失败");
  }
}

async function createCollege() {
  if (!collegeForm.name.trim()) {
    ElMessage.warning("请输入学院名称。");
    return;
  }

  try {
    const result = await post("/admin/colleges", {
      name: collegeForm.name.trim(),
      contactName: collegeForm.contactName.trim(),
      contactPhone: collegeForm.contactPhone.trim(),
      description: collegeForm.description.trim(),
    });
    collegeDialog.value = false;
    resetCollegeForm();
    ElMessage.success(`学院已新增：${result.name}。现在可以继续为该学院创建管理员账号。`);
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function createCollegeAdmin() {
  if (!collegeAdminForm.collegeId) {
    ElMessage.warning("请选择所属学院。");
    return;
  }
  if (!collegeAdminForm.name.trim()) {
    ElMessage.warning("请输入管理员姓名。");
    return;
  }

  try {
    const result = await post("/admin/college-admins", {
      collegeId: collegeAdminForm.collegeId,
      name: collegeAdminForm.name.trim(),
      account: collegeAdminForm.account.trim(),
    });
    collegeAdminDialog.value = false;
    resetCollegeAdminForm();
    ElMessage.success(`学院管理员账号已创建：${result.account}，初始密码为 123456。`);
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
onMounted(loadAll);
watch(() => route.path, loadAll);
watch(applicationStatus, () => {
  applicationPage.value = 1;
});
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <template v-if="section === 'dashboard'">
      <div class="page-header">
        <div>
          <h2>学院管理员总览</h2>
          <div class="subtle">超级管理员作为学校总教秘，负责本校各学院管理员账号的统一录入、开通状态管理与平台治理；账号创建完成后，状态会显示为已开通。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>活跃用户</h4><strong>{{ dashboard.activeUsers || 0 }}</strong></div>
        <div class="metric-card"><h4>学院总数</h4><strong>{{ basicData.colleges?.length || 0 }}</strong></div>
        <div class="metric-card"><h4>待录入学院数</h4><strong>{{ Math.max((basicData.colleges?.length || 0) - collegeAdmins.length, 0) }}</strong></div>
        <div class="metric-card"><h4>全局表单数</h4><strong>{{ dashboard.totalForms || 0 }}</strong></div>
        <div class="metric-card"><h4>未读消息</h4><strong>{{ dashboard.unreadMessages || 0 }}</strong></div>
      </div>
      <div class="dashboard-board" style="margin-top: 18px">
        <div class="panel-card dashboard-panel dashboard-panel-todo">
          <div class="page-header dashboard-panel-header">
            <div>
              <h2 style="font-size: 20px">待办概览</h2>
              <div class="subtle">集中列出当前最需要处理的事项，帮助超级管理员快速安排录入节奏。</div>
            </div>
          </div>
          <div class="dashboard-todo-grid">
            <div v-for="item in dashboardTodoItems" :key="item.label" class="dashboard-todo-item">
              <div class="dashboard-todo-value">{{ item.value }}</div>
              <div class="dashboard-todo-label">{{ item.label }}</div>
              <div class="dashboard-todo-hint">{{ item.hint }}</div>
            </div>
          </div>
          <div class="dashboard-preview-section">
            <div class="dashboard-section-title">待录入学院预览</div>
            <div class="dashboard-preview-list">
              <div v-for="item in pendingCollegePreview" :key="item.name" class="dashboard-preview-item">
                <div>
                  <div class="dashboard-preview-title">{{ item.name }}</div>
                  <div class="dashboard-preview-meta">{{ item.contact }} · {{ item.phone }}</div>
                </div>
              </div>
              <div v-if="!pendingCollegePreview.length" class="dashboard-recent-empty">当前学院都已录入管理员账号</div>
            </div>
          </div>
        </div>
        <div class="panel-card dashboard-panel dashboard-panel-status">
          <div class="page-header dashboard-panel-header">
            <div>
              <h2 style="font-size: 20px">学院状态分布</h2>
              <div class="subtle">通过总量、已录入与待录入的关系，快速判断当前账号补录压力。</div>
            </div>
            <el-tag type="info">{{ collegeTotalCount }}</el-tag>
          </div>
          <div class="dashboard-mini-chart">
            <div class="dashboard-mini-bar dashboard-mini-bar-large">
              <div
                v-for="segment in collegeStatusSegments"
                :key="segment.label"
                class="dashboard-mini-segment"
                :style="{ width: `${segment.percent}%`, background: segment.color }"
              ></div>
            </div>
            <div class="dashboard-mini-legend">
              <div v-for="segment in collegeStatusSegments" :key="segment.label" class="dashboard-mini-legend-item">
                <span class="dashboard-mini-dot" :style="{ background: segment.color }"></span>
                <span class="dashboard-mini-legend-label">{{ segment.label }}</span>
                <span class="dashboard-mini-legend-count">{{ segment.count }} 个</span>
              </div>
            </div>
            <div class="dashboard-mini-foot">学院覆盖率 {{ collegeCoverageRate }}%，已录入账号 {{ collegeAdmins.length }} 个，启用账号 {{ activeCollegeAdminCount }} 个，停用账号 {{ disabledCollegeAdminCount }} 个。</div>
          </div>
        </div>
        <div class="panel-card dashboard-panel dashboard-panel-recent">
          <div class="page-header dashboard-panel-header">
            <div>
              <h2 style="font-size: 20px">最近动态</h2>
              <div class="subtle">展示最近的关键操作，方便追踪平台侧的录入和维护记录。</div>
            </div>
            <el-tag type="success">{{ recentActivities.length }} 条</el-tag>
          </div>
          <div class="dashboard-recent-list dashboard-recent-list-wide">
            <div v-for="item in recentActivities" :key="`${item.time}-${item.title}`" class="dashboard-recent-item">
              <div>
                <div class="dashboard-recent-title">{{ item.title }}</div>
                <div class="dashboard-recent-meta">{{ item.meta }}</div>
              </div>
              <div class="dashboard-recent-time">{{ item.time }}</div>
            </div>
            <div v-if="!recentActivities.length" class="dashboard-recent-empty">暂无最近动态</div>
          </div>
        </div>
      </div>
    </template>

    <template v-else-if="section === 'basic'">
      <div class="page-header">
        <div>
          <h2>学院申请</h2>
          <div class="subtle">展示学院申请与学院管理员账号等基础信息；如需为尚未录入的学院直接创建管理员账号，可先新增学院基础信息，再创建学院管理员账号。</div>
        </div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header">
            <h2 style="font-size: 20px">学院列表</h2>
            <el-tag>{{ basicData.colleges?.length || 0 }}</el-tag>
            <el-button type="primary" color="#0f766e" plain @click="openCreateCollege">新增学院</el-button>
          </div>
          <el-table :data="basicData.colleges || []" max-height="280">
            <el-table-column prop="name" label="学院" min-width="160" />
            <el-table-column prop="contactName" label="联系人" width="110" />
            <el-table-column prop="contactPhone" label="联系电话" width="140" />
            <template #empty><el-empty description="暂无学院基础数据" /></template>
          </el-table>
        </div>
        <div class="panel-card">
          <h2 style="font-size: 20px; margin-bottom: 16px">角色与状态</h2>
          <div class="subtle" style="margin-bottom: 10px">平台角色</div>
          <el-space wrap><el-tag v-for="role in basicData.roles || []" :key="role" type="success">{{ role }}</el-tag></el-space>
          <div class="subtle" style="margin: 20px 0 10px">表单状态</div>
          <el-space wrap><el-tag v-for="status in basicData.formStatuses || []" :key="status" type="info">{{ status }}</el-tag></el-space>
        </div>
      </div>
      <div class="panel-card" style="margin-top: 18px">
        <div class="page-header">
          <h2 style="font-size: 20px">学院管理员账号</h2>
          <el-tag>{{ collegeAdmins.length }}</el-tag>
          <el-button type="primary" color="#0f766e" plain @click="openCreateCollegeAdmin">新增学院管理员</el-button>
        </div>
        <el-table :data="collegeAdmins">
          <el-table-column prop="collegeName" label="学院" min-width="150" />
          <el-table-column prop="name" label="管理员" width="120" />
          <el-table-column prop="account" label="账号" width="140" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === "ACTIVE" ? "启用" : "停用" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="改密状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.mustChangePassword ? 'warning' : 'success'">{{ row.mustChangePassword ? "待改密" : "正常" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginAt" label="最近登录" width="180" />
          <el-table-column label="操作" min-width="180">
            <template #default="{ row }">
              <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleCollegeAdminStatus(row)">{{ row.status === "ACTIVE" ? "停用" : "启用" }}</el-button>
              <el-button link type="primary" @click="resetCollegeAdminPassword(row)">重置密码</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无学院管理员账号" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'params'">
      <div class="page-header">
        <div>
          <h2>参数配置</h2>
          <div class="subtle">当前支持配置预警阈值、预警级别、是否允许催办以及催办文案模板，满足二期“提醒规则尽量配置化”的要求。</div>
        </div>
        <el-button type="primary" color="#0f766e" :loading="settingsSaving" @click="saveSystemSettings">保存参数</el-button>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>提醒参数</h4><strong>{{ reminderSettings.length }}</strong></div>
        <div class="metric-card"><h4>启用催办规则</h4><strong>{{ enabledReminderCount }}</strong></div>
      </div>
      <div class="panel-card">
        <div v-for="item in reminderSettings" :key="item.key" class="panel-card" style="margin-bottom: 14px; border: 1px solid rgba(15, 118, 110, 0.12)">
          <div class="page-header">
            <div>
              <h2 style="font-size: 18px">{{ item.name }}</h2>
              <div class="subtle">{{ item.description }}</div>
            </div>
            <el-tag type="info">{{ item.key }}</el-tag>
          </div>
          <el-row :gutter="16" style="margin-top: 12px">
            <el-col :span="12">
              <el-form-item label="参数值">
                <el-input-number
                  v-if="item.valueType === 'INTEGER'"
                  v-model="settingsForm[item.key]"
                  :min="0"
                  :max="30"
                  style="width: 100%"
                />
                <el-switch
                  v-else-if="item.valueType === 'BOOLEAN'"
                  v-model="settingsForm[item.key]"
                  inline-prompt
                  active-text="启用"
                  inactive-text="关闭"
                />
                <el-select v-else-if="item.valueType === 'SELECT'" v-model="settingsForm[item.key]" style="width: 100%">
                  <el-option v-for="option in item.options || []" :key="`${item.key}-${option.value}`" :label="option.label" :value="option.value" />
                </el-select>
                <el-input
                  v-else
                  v-model="settingsForm[item.key]"
                  type="textarea"
                  :rows="item.key.endsWith('_content_template') ? 4 : 2"
                  placeholder="请输入模板内容，可使用 {title}、{targetName}、{overdueDays} 等变量"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="最近更新时间">
                <el-input :model-value="item.updatedAt || '未更新'" readonly />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </div>
    </template>
    <template v-else-if="section === 'templates'">
      <div class="page-header">
        <div>
          <h2>表单模板配置</h2>
          <div class="subtle">二期起步功能。支持按实习类型配置模板、字段结构、启停状态和展示顺序。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="openCreateTemplate">新增模板</el-button>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>模板总数</h4><strong>{{ formTemplates.length }}</strong></div>
        <div class="metric-card"><h4>启用模板</h4><strong>{{ enabledTemplateCount }}</strong></div>
        <div class="metric-card"><h4>停用模板</h4><strong>{{ disabledTemplateCount }}</strong></div>
      </div>
      <FilterTablePanel
        v-model:keyword="templateKeyword"
        v-model:current-page="templatePage"
        placeholder="筛选编码、名称、分类、说明、适用类型"
        input-width="360px"
        :total="filteredTemplates.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedTemplates">
          <el-table-column prop="code" label="模板编码" min-width="160" />
          <el-table-column prop="name" label="模板名称" min-width="150" />
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column label="适用类型" min-width="180">
            <template #default="{ row }">
              <el-space wrap>
                <el-tag v-for="item in row.applicableTypes || []" :key="item" type="success">{{ item }}</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? "启用" : "停用" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditTemplate(row)">编辑</el-button>
              <el-button link :type="row.enabled ? 'danger' : 'success'" @click="toggleTemplateStatus(row)">{{ row.enabled ? "停用" : "启用" }}</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无表单模板配置" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'logs'">
      <div class="page-header">
        <div>
          <h2>日志审计</h2>
          <div class="subtle">查看近期登录日志和关键业务动作留痕，帮助平台侧排查操作责任链路。</div>
        </div>
      </div>
      <FilterTablePanel
        v-model:keyword="logKeyword"
        v-model:current-page="logPage"
        placeholder="筛选类型、动作、详情、操作人"
        input-width="340px"
        :total="filteredLogs.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedLogs">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="action" label="动作" min-width="180" />
          <el-table-column prop="detail" label="详情" min-width="320" />
          <el-table-column prop="operatorId" label="操作人" width="150" />
          <el-table-column prop="createdAt" label="时间" width="180" />
          <template #empty><el-empty description="暂无日志记录" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <el-dialog v-model="reviewDialog" title="处理学院账号开通记录" width="520px">
      <el-form label-position="top">
        <el-form-item label="处理结论"><el-switch v-model="reviewForm.approved" inline-prompt active-text="开通" inactive-text="驳回" /></el-form-item>
        <el-form-item label="处理备注"><el-input v-model="reviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitReview">提交处理</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="reviewResultDialog" title="处理结果" width="560px">
      <el-alert
        :title="reviewResult && reviewResult.approved ? '学院账号已开通' : '学院账号未开通'"
        :type="reviewResult && reviewResult.approved ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <el-descriptions v-if="reviewResult" :column="1" border style="margin-top: 16px">
        <el-descriptions-item label="学院">{{ reviewResult.collegeName }}</el-descriptions-item>
        <el-descriptions-item label="处理状态">{{ getCollegeRecordStatusMeta(reviewResult.status).label }}</el-descriptions-item>
        <el-descriptions-item label="处理备注">{{ reviewResult.reviewComment || '无' }}</el-descriptions-item>
        <el-descriptions-item v-if="reviewResult.approved" label="账号开通结果">
          {{ reviewResult.generatedCollegeAdmin ? '已自动创建学院管理员账号' : '已关联现有学院管理员账号' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="reviewResult.approved" label="学院管理员账号">
          <el-tag type="success">{{ reviewResult.collegeAdminAccount }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="reviewResult.approved" label="管理员姓名">
          {{ reviewResult.collegeAdminName }}
        </el-descriptions-item>
        <el-descriptions-item v-if="reviewResult.approved && reviewResult.defaultPassword" label="初始密码">
          <el-tag type="warning">{{ reviewResult.defaultPassword }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="reviewResult.approved" label="首次登录策略">
          {{ reviewResult.mustChangePassword ? '首次登录必须修改密码' : '允许直接进入系统' }}
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="reviewResult && reviewResult.approved" class="subtle" style="margin-top: 14px">
        处理完成后，记录状态已自动切换为“已开通”；建议立即将账号信息通知学院联系人，并提醒其首次登录后尽快修改密码。
      </div>
      <template #footer>
        <el-button type="primary" color="#0f766e" @click="reviewResultDialog = false">我知道了</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="collegeDialog" title="新增学院" width="520px">
      <el-form label-position="top">
        <el-form-item label="学院名称">
          <el-input v-model="collegeForm.name" placeholder="请输入学院名称" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="collegeForm.contactName" placeholder="可选，便于后续管理员录入" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="collegeForm.contactPhone" placeholder="可选，便于后续联系" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="collegeForm.description" type="textarea" :rows="3" placeholder="可选，记录学院基础信息或备注" />
        </el-form-item>
        <div class="subtle">新增学院后，可继续在下方“学院管理员账号”区域为该学院创建管理员账号。</div>
      </el-form>
      <template #footer>
        <el-button @click="collegeDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="createCollege">确认新增</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="collegeAdminDialog" title="新增学院管理员" width="520px">
      <el-form label-position="top">
        <el-form-item label="所属学院">
          <el-select v-model="collegeAdminForm.collegeId" style="width: 100%" placeholder="请选择学院">
            <el-option
              v-for="item in availableCollegesForAdmin"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="管理员姓名">
          <el-input v-model="collegeAdminForm.name" placeholder="请输入管理员姓名" />
        </el-form-item>
        <el-form-item label="登录账号">
          <el-input v-model="collegeAdminForm.account" placeholder="可选，不填则系统自动生成" />
        </el-form-item>
        <div class="subtle">初始密码默认为 123456，创建后将要求首次登录修改密码。</div>
      </el-form>
      <template #footer>
        <el-button @click="collegeAdminDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="createCollegeAdmin">确认创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateDialog" :title="templateMode === 'create' ? '新增表单模板' : '编辑表单模板'" width="880px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板编码">
              <el-input v-model="templateForm.code" :disabled="templateMode === 'edit'" placeholder="例如：weekly-summary" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板名称">
              <el-input v-model="templateForm.name" placeholder="请输入模板名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="模板分类">
              <el-select v-model="templateForm.category" style="width: 100%">
                <el-option label="通用" value="COMMON" />
                <el-option label="任课实习" value="TEACHING" />
                <el-option label="班主任实习" value="HEAD_TEACHER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="适用实习类型">
              <el-select v-model="templateForm.applicableTypes" multiple style="width: 100%">
                <el-option label="任课实习" value="TEACHING" />
                <el-option label="班主任实习" value="HEAD_TEACHER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="排序">
              <el-input-number v-model="templateForm.sortNo" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="启用">
              <el-switch v-model="templateForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="模板说明">
          <el-input v-model="templateForm.description" type="textarea" :rows="3" placeholder="请输入模板说明" />
        </el-form-item>

        <div class="page-header" style="margin-top: 10px">
          <div>
            <h2 style="font-size: 18px">字段配置</h2>
            <div class="subtle">当前版本支持 `text`、`textarea`、`date` 三种字段类型。</div>
          </div>
          <el-button plain @click="addTemplateField">新增字段</el-button>
        </div>

        <div v-for="(field, index) in templateForm.fieldSchema" :key="`${index}-${field.key}`" class="panel-card" style="margin-top: 12px">
          <div class="page-header">
            <h2 style="font-size: 16px">字段 {{ index + 1 }}</h2>
            <el-button link type="danger" @click="removeTemplateField(index)">删除</el-button>
          </div>
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="字段编码">
                <el-input v-model="field.key" placeholder="例如：title" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="字段名称">
                <el-input v-model="field.label" placeholder="例如：标题" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="字段类型">
                <el-select v-model="field.type" style="width: 100%">
                  <el-option label="单行文本" value="text" />
                  <el-option label="多行文本" value="textarea" />
                  <el-option label="日期" value="date" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="是否必填">
                <el-switch v-model="field.required" inline-prompt active-text="必填" inactive-text="选填" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="占位提示">
            <el-input v-model="field.placeholder" placeholder="请输入表单占位提示" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="templateDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitTemplate">{{ templateMode === "create" ? "创建模板" : "保存修改" }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>











