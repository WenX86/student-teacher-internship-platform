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
const settingsSaving = ref(false);
const templateMode = ref("create");
const currentRow = ref(null);
const reviewForm = reactive({ approved: true, comment: "" });
const reviewResult = ref(null);
const templateForm = reactive(createEmptyTemplateForm());
const settingsForm = reactive({});

const pageSize = 6;
const applicationStatus = ref("ALL");
const applicationStatusOptions = [
  { label: "全部状态", value: "ALL" },
  { label: "只看待审核", value: "待审核" },
  { label: "只看已通过", value: "已通过" },
  { label: "只看已驳回", value: "已驳回" },
];
const applicationSource = computed(() => {
  if (applicationStatus.value === "ALL") {
    return collegeApplications.value;
  }
  return collegeApplications.value.filter((item) => item.status === applicationStatus.value);
});

const {
  keyword: applicationKeyword,
  currentPage: applicationPage,
  filteredItems: filteredCollegeApplications,
  pagedItems: pagedCollegeApplications,
} = useFilteredPagination({
  source: applicationSource,
  matcher: (item) => [item.schoolName, item.collegeName, item.contactName, item.contactPhone, item.status],
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
    ElMessage.success("学院入驻申请已处理。");
    reviewDialog.value = false;
    reviewResultDialog.value = true;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function findCollegeAdminForApplication(row) {
  return collegeAdmins.value.find((item) => item.schoolName === row.schoolName && item.collegeName === row.collegeName) || null;
}

function reviewCreatedNewCollegeAdmin(row) {
  return String(row.reviewComment || "").includes("初始密码：123456");
}

function reviewHasCollegeAdmin(row) {
  return String(row.reviewComment || "").includes("学院管理员账号：");
}

function openCreateTemplate() {
  templateMode.value = "create";
  resetTemplateForm();
  templateDialog.value = true;
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
          <h2>平台入驻审核</h2>
          <div class="subtle">超级管理员负责学院入驻审批、全局启用情况监控和一期平台治理总览。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>活跃用户</h4><strong>{{ dashboard.activeUsers || 0 }}</strong></div>
        <div class="metric-card"><h4>学院申请数</h4><strong>{{ dashboard.collegeApplicationCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待审核申请</h4><strong>{{ dashboard.pendingCollegeApplicationCount || 0 }}</strong></div>
        <div class="metric-card"><h4>全局表单数</h4><strong>{{ dashboard.totalForms || 0 }}</strong></div>
        <div class="metric-card"><h4>未读消息</h4><strong>{{ dashboard.unreadMessages || 0 }}</strong></div>
      </div>
      <FilterTablePanel
        v-model:keyword="applicationKeyword"
        v-model:current-page="applicationPage"
        placeholder="筛选学校、学院、联系人、状态"
        input-width="340px"
        :total="filteredCollegeApplications.length"
        :page-size="pageSize"
      >
        <template #toolbar-extra>
          <el-select v-model="applicationStatus" style="width: 180px">
            <el-option v-for="option in applicationStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </template>
        <el-table :data="pagedCollegeApplications">
          <el-table-column type="expand" width="54">
            <template #default="{ row }">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="申请说明" :span="2">{{ row.description || "未填写" }}</el-descriptions-item>
                <el-descriptions-item label="审核意见" :span="2">{{ row.reviewComment || "暂无审核意见" }}</el-descriptions-item>
                <el-descriptions-item label="账号处理结果" :span="2">
                  {{ row.status === "已通过" ? (reviewCreatedNewCollegeAdmin(row) ? "审核通过时已自动创建学院管理员账号" : (reviewHasCollegeAdmin(row) ? "审核通过时已关联现有学院管理员账号" : "审核通过，但未识别到关联账号信息")) : "当前审核状态下未生成学院管理员账号" }}
                </el-descriptions-item>
                <el-descriptions-item label="学院管理员账号">
                  <template v-if="findCollegeAdminForApplication(row)">
                    <el-tag type="success">{{ findCollegeAdminForApplication(row).account }}</el-tag>
                  </template>
                  <span v-else>暂无</span>
                </el-descriptions-item>
                <el-descriptions-item label="管理员姓名">
                  {{ findCollegeAdminForApplication(row)?.name || "暂无" }}
                </el-descriptions-item>
                <el-descriptions-item label="账号状态">
                  <template v-if="findCollegeAdminForApplication(row)">
                    <el-tag :type="findCollegeAdminForApplication(row).status === 'ACTIVE' ? 'success' : 'info'">
                      {{ findCollegeAdminForApplication(row).status === "ACTIVE" ? "启用" : "停用" }}
                    </el-tag>
                  </template>
                  <span v-else>暂无</span>
                </el-descriptions-item>
                <el-descriptions-item label="首次登录策略">
                  <template v-if="findCollegeAdminForApplication(row)">
                    {{ findCollegeAdminForApplication(row).mustChangePassword ? "首次登录必须修改密码" : "允许直接进入系统" }}
                  </template>
                  <span v-else>暂无</span>
                </el-descriptions-item>
                <el-descriptions-item label="初始密码说明" :span="2">
                  {{ reviewCreatedNewCollegeAdmin(row) ? "本次审核自动创建账号时的初始密码为 123456，请提醒学院联系人首次登录后立即修改。" : "当前记录未展示新的初始密码信息。" }}
                </el-descriptions-item>
              </el-descriptions>
            </template>
          </el-table-column>
          <el-table-column prop="schoolName" label="学校" min-width="180" />
          <el-table-column prop="collegeName" label="学院" min-width="180" />
          <el-table-column prop="contactName" label="联系人" width="110" />
          <el-table-column prop="contactPhone" label="联系电话" width="140" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openReview(row)">审核</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无学院入驻申请" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'basic'">
      <div class="page-header">
        <div>
          <h2>基础数据快照</h2>
          <div class="subtle">展示平台侧学院、角色和表单状态等全局基础配置，便于核查一期主数据。</div>
        </div>
      </div>
      <div class="dual-grid">
        <div class="panel-card">
          <div class="page-header"><h2 style="font-size: 20px">学院列表</h2><el-tag>{{ basicData.colleges?.length || 0 }}</el-tag></div>
          <el-table :data="basicData.colleges || []">
            <el-table-column prop="schoolName" label="学校" min-width="180" />
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
        </div>
        <el-table :data="collegeAdmins">
          <el-table-column prop="schoolName" label="学校" min-width="160" />
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

    <el-dialog v-model="reviewDialog" title="审核学院入驻申请" width="520px">
      <el-form label-position="top">
        <el-form-item label="审核结论"><el-switch v-model="reviewForm.approved" inline-prompt active-text="通过" inactive-text="驳回" /></el-form-item>
        <el-form-item label="审核意见"><el-input v-model="reviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitReview">提交审核</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="reviewResultDialog" title="审核结果" width="560px">
      <el-alert
        :title="reviewResult && reviewResult.approved ? '学院入驻审核已通过' : '学院入驻审核已驳回'"
        :type="reviewResult && reviewResult.approved ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <el-descriptions v-if="reviewResult" :column="1" border style="margin-top: 16px">
        <el-descriptions-item label="学校">{{ reviewResult.schoolName }}</el-descriptions-item>
        <el-descriptions-item label="学院">{{ reviewResult.collegeName }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">{{ reviewResult.status }}</el-descriptions-item>
        <el-descriptions-item label="审核意见">{{ reviewResult.reviewComment || '无' }}</el-descriptions-item>
        <el-descriptions-item v-if="reviewResult.approved" label="账号处理结果">
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
        建议审核通过后立即将账号信息通知学院联系人，并提醒其首次登录后尽快修改密码。
      </div>
      <template #footer>
        <el-button type="primary" color="#0f766e" @click="reviewResultDialog = false">我知道了</el-button>
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
