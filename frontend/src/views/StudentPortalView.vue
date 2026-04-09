<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { ElMessageBox } from "element-plus";
import { downloadFile, get, post, put, uploadFile } from "../api/http";
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";
import { useMessageStore } from "../stores/message";
import { getMessageTypeMeta, getReadStatusMeta, getStatusMeta } from "../utils/status";

const route = useRoute();
const messageStore = useMessageStore();
const loading = ref(false);
const dashboard = ref({});
const mentorApplications = ref([]);
const teachers = ref([]);
const internshipApplications = ref([]);
const organizations = ref([]);
const templates = ref([]);
const forms = ref([]);
const messages = ref([]);
const evaluations = ref([]);
const attachmentUploading = reactive({
  internship: false,
  form: false,
});

const mentorDialogVisible = ref(false);
const internshipDialogVisible = ref(false);
const formDialogVisible = ref(false);
const editingFormId = ref("");
const editingArchivedForm = ref(false);
const modificationRequestDialogVisible = ref(false);
const modificationRequestForm = reactive({ reason: "" });
const modificationRequestRow = ref(null);

const mentorForm = reactive({
  teacherId: "",
  studentRemark: "",
});

const internshipForm = reactive({
  organizationId: "",
  batchName: "2026年春季学期",
  position: "",
  gradeTarget: "",
  startDate: "",
  endDate: "",
  remark: "",
  attachments: [],
});

const formModel = reactive({
  templateCode: "",
  content: {},
  submit: true,
  attachments: [],
});

const pageSize = 10;

const section = computed(() => route.meta.section || "dashboard");
const latestForms = computed(() => forms.value.slice(0, 5));
const unreadMessages = computed(() => messages.value.filter((item) => !item.read));
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
const selectedTemplate = computed(() => templates.value.find((item) => item.code === formModel.templateCode) || null);
const hasEffectiveMentor = computed(() => mentorApplications.value.some((item) => item.status === "已生效"));
const hasApprovedInternship = computed(() => dashboard.value?.internshipStatus === "实习中" || internshipApplications.value.some((item) => item.status === "已通过"));
const canCreateForm = computed(() => hasEffectiveMentor.value && hasApprovedInternship.value);
const formCreationHint = computed(() => {
  if (!hasEffectiveMentor.value && !hasApprovedInternship.value) {
    return "请先完成指导关系生效和实习申请审批通过后再新建表单。";
  }
  if (!hasEffectiveMentor.value) {
    return "请先完成指导关系生效后再新建表单。";
  }
  if (!hasApprovedInternship.value) {
    return "请先完成实习申请审批通过后再新建表单。";
  }
  return "";
});
const editableFormStatuses = ["草稿", "教师退回", "学院退回", "允许修改"];
const templateFields = computed(() => {
  if (selectedTemplate.value?.fieldSchema?.length) {
    return selectedTemplate.value.fieldSchema;
  }
  return createDefaultFields();
});

const {
  keyword: formKeyword,
  currentPage: formPage,
  filteredItems: filteredForms,
  pagedItems: pagedForms,
} = useFilteredPagination({
  source: forms,
  matcher: (item) => [item.templateName, item.status, ...Object.values(item.content || {})],
  pageSize,
});

const {
  keyword: internshipKeyword,
  currentPage: internshipPage,
  filteredItems: filteredInternships,
  pagedItems: pagedInternships,
} = useFilteredPagination({
  source: internshipApplications,
  matcher: (item) => [item.organization?.name, item.position, item.gradeTarget, item.status],
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

async function loadAll() {
  loading.value = true;
  try {
    const [
      dashboardData,
      mentorData,
      teacherData,
      organizationData,
      internshipData,
      templateData,
      formData,
      messageData,
      evaluationData,
    ] = await Promise.all([
      get("/dashboard"),
      get("/mentor-applications"),
      get("/teachers"),
      get("/organizations"),
      get("/internship-applications"),
      get("/form-templates"),
      get("/forms"),
      get("/messages"),
      get("/evaluations"),
    ]);

    dashboard.value = dashboardData;
    messageStore.syncUnreadCount(dashboardData?.unreadMessages || 0);
    mentorApplications.value = mentorData;
    teachers.value = teacherData;
    organizations.value = organizationData;
    internshipApplications.value = internshipData;
    templates.value = templateData;
    forms.value = formData;
    messages.value = messageData;
    evaluations.value = evaluationData;
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}

function cloneAttachments(items) {
  return Array.isArray(items) ? items.map((item) => ({ ...item })) : [];
}

function resetMentorForm() {
  mentorForm.teacherId = "";
  mentorForm.studentRemark = "";
}

function resetInternshipForm() {
  internshipForm.organizationId = "";
  internshipForm.batchName = "2026年春季学期";
  internshipForm.position = "";
  internshipForm.gradeTarget = "";
  internshipForm.startDate = "";
  internshipForm.endDate = "";
  internshipForm.remark = "";
  internshipForm.attachments = [];
}

function resetFormModel() {
  editingFormId.value = "";
  editingArchivedForm.value = false;
  formModel.templateCode = "";
  syncFormContent([]);
  formModel.submit = true;
  formModel.attachments = [];
}

function createDefaultFields() {
  return [
    { key: "title", label: "标题", type: "text", required: true, placeholder: "请输入标题" },
    { key: "summary", label: "摘要", type: "textarea", required: true, placeholder: "请输入摘要" },
  ];
}

function syncFormContent(schema, source = {}) {
  const next = {};
  (schema.length ? schema : createDefaultFields()).forEach((field) => {
    next[field.key] = source[field.key] ?? "";
  });
  Object.keys(formModel.content).forEach((key) => delete formModel.content[key]);
  Object.assign(formModel.content, next);
}

function handleTemplateChange() {
  syncFormContent(templateFields.value);
}

function openFormDialog() {
  if (!canCreateForm.value) {
    ElMessage.warning(formCreationHint.value || "请先完成前置条件");
    return;
  }
  resetFormModel();
  formDialogVisible.value = true;
}

function openModificationRequestDialog(row) {
  modificationRequestRow.value = row;
  modificationRequestForm.reason = "";
  modificationRequestDialogVisible.value = true;
}

function formatFileSize(size) {
  const value = Number(size || 0);
  if (value >= 1024 * 1024) {
    return `${(value / (1024 * 1024)).toFixed(2)} MB`;
  }
  if (value >= 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${value} B`;
}

function attachmentsFor(target) {
  return target === "internship" ? internshipForm.attachments : formModel.attachments;
}

function setAttachments(target, items) {
  if (target === "internship") {
    internshipForm.attachments = items;
    return;
  }
  formModel.attachments = items;
}

async function handleAttachmentChange(event, target) {
  const files = Array.from(event.target.files || []);
  event.target.value = "";
  if (!files.length) {
    return;
  }

  attachmentUploading[target] = true;
  let successCount = 0;
  let firstError = "";

  try {
    for (const file of files) {
      try {
        const uploaded = await uploadFile(file);
        setAttachments(target, [...attachmentsFor(target), uploaded]);
        successCount += 1;
      } catch (error) {
        if (!firstError) {
          firstError = `${file.name} 上传失败：${error.message}`;
        }
      }
    }

    if (successCount) {
      ElMessage.success(`${successCount} 个附件上传成功`);
    }
    if (firstError) {
      ElMessage.error(firstError);
    }
  } finally {
    attachmentUploading[target] = false;
  }
}

function removeAttachment(target, index) {
  const next = attachmentsFor(target).filter((_, itemIndex) => itemIndex !== index);
  setAttachments(target, next);
}

async function handleDownloadAttachment(item) {
  try {
    await downloadFile(item.downloadUrl || `/files/${item.storedName}`, item.name || "附件");
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function submitMentorApplication() {
  if (!mentorForm.teacherId) {
    ElMessage.warning("请选择要下载的附件");
    return;
  }

  try {
    await post("/mentor-applications", mentorForm);
    ElMessage.success("附件下载已开始");
    mentorDialogVisible.value = false;
    resetMentorForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openInternshipDialog() {
  if (!hasEffectiveMentor.value) {
    ElMessage.warning("请先确保指导关系已生效后再发起实习申请");
    return;
  }
  resetInternshipForm();
  internshipDialogVisible.value = true;
}

async function submitInternshipApplication() {
  if (!hasEffectiveMentor.value) {
    ElMessage.warning("请先选择实习单位和岗位信息");
    return;
  }

  if (!internshipForm.organizationId || !internshipForm.position || !internshipForm.gradeTarget) {
    ElMessage.warning("请填写完整的实习起止时间");
    return;
  }

  if (!internshipForm.startDate || !internshipForm.endDate) {
    ElMessage.warning("请至少上传一个实习申请附件");
    return;
  }

  try {
    await post("/internship-applications", {
      organizationId: internshipForm.organizationId,
      batchName: internshipForm.batchName,
      position: internshipForm.position,
      gradeTarget: internshipForm.gradeTarget,
      startDate: internshipForm.startDate,
      endDate: internshipForm.endDate,
      remark: internshipForm.remark,
      attachments: cloneAttachments(internshipForm.attachments),
    });
    ElMessage.success("实习申请提交成功");
    internshipDialogVisible.value = false;
    resetInternshipForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function editExistingForm(row) {
  editingFormId.value = row.id;
  editingArchivedForm.value = row.status === "允许修改";
  formModel.templateCode = row.templateCode;
  const template = templates.value.find((item) => item.code === row.templateCode);
  syncFormContent(template?.fieldSchema || createDefaultFields(), row.content || {});
  formModel.submit = true;
  formModel.attachments = cloneAttachments(row.attachments);
  formDialogVisible.value = true;
}

function canEditForm(row) {
  return editableFormStatuses.includes(row?.status);
}

function canRequestModification(row) {
  return row?.status === "已归档";
}

async function submitForm() {
  if (!formModel.templateCode) {
    ElMessage.warning("请先选择表单模板");
    return;
  }
  if (!editingFormId.value && !canCreateForm.value) {
    ElMessage.warning(formCreationHint.value || "请先完成前置条件");
    return;
  }

  for (const field of templateFields.value) {
    const value = `${formModel.content[field.key] ?? ""}`.trim();
    if (field.required && !value) {
      ElMessage.warning(`请填写 ${field.label}`);
      return;
    }
  }

  try {
    const payload = {
      templateCode: formModel.templateCode,
      content: { ...formModel.content },
      submit: formModel.submit,
      attachments: cloneAttachments(formModel.attachments),
    };

    if (editingFormId.value) {
      await put(`/forms/${editingFormId.value}`, payload);
      ElMessage.success("表单草稿已保存");
    } else {
      await post("/forms", payload);
      ElMessage.success("表单提交成功");
    }

    formDialogVisible.value = false;
    resetFormModel();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function submitFormModificationRequest() {
  if (!modificationRequestRow.value) {
    ElMessage.warning("请先选择要修改的表单");
    return;
  }
  if (!modificationRequestForm.reason.trim()) {
    ElMessage.warning("请填写修改原因");
    return;
  }

  try {
    await post(`/forms/${modificationRequestRow.value.id}/modification-request`, {
      reason: modificationRequestForm.reason,
    });
    ElMessage.success("修改申请已提交，请等待学院审批");
    modificationRequestDialogVisible.value = false;
    modificationRequestRow.value = null;
    modificationRequestForm.reason = "";
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

async function markAllRead() {
  if (!unreadMessages.value.length) {
    ElMessage.info("当前没有未读消息。");
    return;
  }

  try {
    await ElMessageBox.confirm(`确认将 ${unreadMessages.value.length} 条未读消息全部标记为已读？`, "全部已读", {
      confirmButtonText: "全部已读",
      cancelButtonText: "取消",
      type: "warning",
    });
    const result = await post("/messages/read-all", {});
    ElMessage.success(`已将 ${result?.updatedCount || unreadMessages.value.length} 条消息标记为已读`);
    await loadAll();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(error.message);
    }
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
          <h2>学生工作台</h2>
          <div class="subtle">查看指导关系、实习状态、待办任务和消息提醒。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>当前实习状态</h4><strong>{{ dashboard.internshipStatus || "未开始" }}</strong></div>
        <div class="metric-card"><h4>指导关系状态</h4><strong>{{ dashboard.mentorStatus || "未申请" }}</strong></div>
        <div class="metric-card"><h4>待办任务</h4><strong>{{ dashboard.todoCount || 0 }}</strong></div>
        <div class="metric-card"><h4>已归档表单</h4><strong>{{ dashboard.archivedCount || 0 }}</strong></div>
      </div>

      <div class="panel-card">
        <div class="page-header">
          <h2 style="font-size: 20px">最近表单</h2>
          <div style="display: grid; gap: 6px; justify-items: end">
            <el-button type="primary" color="#0f766e" :disabled="!canCreateForm" @click="openFormDialog">新建表单</el-button>
            <span v-if="!canCreateForm" class="subtle">{{ formCreationHint }}</span>
          </div>
        </div>
        <el-table :data="latestForms" style="margin-top: 16px">
          <el-table-column prop="templateName" label="模板名称" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.status).type">{{ getStatusMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" />
          <template #empty>
            <el-empty description="暂无表单记录" />
          </template>
        </el-table>
      </div>

      <div class="panel-card">
        <div class="page-header">
          <h2 style="font-size: 20px">未读消息</h2>
          <div style="display: flex; align-items: center; gap: 10px">
            <el-tag type="warning">{{ unreadMessages.length }} 条</el-tag>
            <el-button link type="primary" :disabled="!unreadMessages.length" @click="markAllRead">全部已读</el-button>
          </div>
        </div>
        <el-table :data="unreadMessages" style="margin-top: 16px">
          <el-table-column label="消息类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getMessageTypeMeta(row.type).type">{{ getMessageTypeMeta(row.type).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="消息标题" />
          <el-table-column prop="createdAt" label="创建时间" width="200" />
          <template #empty>
            <el-empty description="暂无未读消息" />
          </template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'mentor'">
      <div class="page-header">
        <div>
          <h2>指导教师申请</h2>
          <div class="subtle">选择指导教师后发起申请，待教师确认并由学院复核后生效。</div>
        </div>
         <el-button type="primary" color="#0f766e" @click="mentorDialogVisible = true">新建申请</el-button>
      </div>
      <div class="panel-card">
        <el-table :data="mentorApplications">
          <el-table-column label="指导教师" min-width="160">
            <template #default="{ row }">{{ row.teacher?.name }} / {{ row.teacher?.employeeNo }}</template>
          </el-table-column>
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.status).type">{{ getStatusMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="studentRemark" label="学生备注" min-width="200" />
          <el-table-column prop="teacherRemark" label="教师备注" min-width="160" />
          <el-table-column prop="collegeRemark" label="学院备注" min-width="160" />
          <template #empty>
            <el-empty description="暂无指导申请记录" />
          </template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'internship'">
      <div class="page-header">
        <div>
          <h2>实习申请</h2>
          <div class="subtle">选择实习单位并提交学院审批，学院会同步记录单位确认结果。</div>
        </div>
        <div style="display: grid; gap: 6px; justify-items: end">
          <el-button type="primary" color="#0f766e" :disabled="!hasEffectiveMentor" @click="openInternshipDialog">新建实习申请</el-button>
          <span v-if="!hasEffectiveMentor" class="subtle">指导关系生效后才能发起实习申请。</span>
        </div>
      </div>
      <FilterTablePanel
        v-model:keyword="internshipKeyword"
        v-model:current-page="internshipPage"
        placeholder="搜索单位、岗位或审批状态"
        :total="filteredInternships.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedInternships" style="margin-top: 16px">
          <el-table-column label="实习单位" min-width="180">
            <template #default="{ row }">{{ row.organization?.name }}</template>
          </el-table-column>
          <el-table-column prop="position" label="实习岗位" width="140" />
          <el-table-column prop="gradeTarget" label="年级目标" width="120" />
          <el-table-column label="审批状态" width="140">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.status).type">{{ getStatusMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="附件列表" min-width="220">
            <template #default="{ row }">
              <el-space v-if="row.attachments?.length" wrap>
                <el-button
                  v-for="item in row.attachments"
                  :key="item.id || item.storedName || item.name"
                  link
                  type="primary"
                  @click="handleDownloadAttachment(item)"
                >
                  {{ item.name }}
                </el-button>
              </el-space>
              <span v-else class="subtle">未上传附件</span>
            </template>
          </el-table-column>
          <el-table-column prop="organizationConfirmation" label="单位确认情况" width="140" />
          <el-table-column prop="reviewComment" label="学院审核意见" min-width="200" />
          <template #empty>
            <el-empty description="暂无实习申请记录" />
          </template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'forms'">
      <div class="page-header">
        <div>
          <h2>表单管理</h2>
          <div class="subtle">统一管理通用表单与实习过程表单，按业务流程逐级流转。</div>
        </div>
        <div style="display: grid; gap: 6px; justify-items: end">
            <el-button type="primary" color="#0f766e" :disabled="!canCreateForm" @click="openFormDialog">新建表单</el-button>
            <span v-if="!canCreateForm" class="subtle">{{ formCreationHint }}</span>
          </div>
      </div>
      <FilterTablePanel
        v-model:keyword="formKeyword"
        v-model:current-page="formPage"
        placeholder="搜索模板、标题或状态"
        :total="filteredForms.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedForms" style="margin-top: 16px">
          <el-table-column prop="templateName" label="模板名称" min-width="160" />
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.status).type">{{ getStatusMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="90" />
          <el-table-column label="表单得分" width="100">
            <template #default="{ row }">{{ row.score ?? "未评分" }}</template>
          </el-table-column>
          <el-table-column label="标题" min-width="180">
            <template #default="{ row }">{{ row.content?.title }}</template>
          </el-table-column>
          <el-table-column label="附件列表" min-width="220">
            <template #default="{ row }">
              <el-space v-if="row.attachments?.length" wrap>
                <el-button
                  v-for="item in row.attachments"
                  :key="item.id || item.storedName || item.name"
                  link
                  type="primary"
                  @click="handleDownloadAttachment(item)"
                >
                  {{ item.name }}
                </el-button>
              </el-space>
              <span v-else class="subtle">未上传附件</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="canEditForm(row)" link type="primary" @click="editExistingForm(row)">{{ row.status === "允许修改" ? "修改" : "编辑" }}</el-button>
              <el-button v-else-if="canRequestModification(row)" link type="warning" @click="openModificationRequestDialog(row)">申请修改</el-button>
              <span v-else-if="row.status === '修改申请中'" class="subtle">申请中</span>
              <span v-else class="subtle">当前状态不可编辑</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无表单记录" />
          </template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'messages'">
      <div class="page-header">
        <div>
          <h2>消息中心</h2>
          <div class="subtle">集中查看待办提醒、审核结果和退回通知。</div>
        </div>
        <div style="display: flex; align-items: center; gap: 10px">
          <el-tag type="warning">未读 {{ unreadMessages.length }} 条</el-tag>
          <el-button link type="primary" :disabled="!unreadMessages.length" @click="markAllRead">全部已读</el-button>
        </div>
      </div>
      <FilterTablePanel
        v-model:keyword="messageKeyword"
        v-model:current-page="messagePage"
        placeholder="筛选类型、标题或内容"
        :total="filteredMessages.length"
        :page-size="pageSize"
      >
        <template #toolbar-extra>
          <el-select v-model="messageReadFilter" style="width: 140px">
            <el-option v-for="option in messageReadOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </template>
        <el-table :data="pagedMessages" style="margin-top: 16px">
          <el-table-column label="绫诲瀷" width="120">
            <template #default="{ row }">
              <el-tag :type="getMessageTypeMeta(row.type).type">{{ getMessageTypeMeta(row.type).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getReadStatusMeta(row.read).type">{{ getReadStatusMeta(row.read).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="!row.read" link type="primary" @click="markRead(row)">标记已读</el-button>
              <span v-else class="subtle">已处理</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无消息" />
          </template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'results'">
      <div class="page-header">
        <div>
          <h2>结果查看</h2>
          <div class="subtle">查看指导教师评价和学院最终确认结果。</div>
        </div>
      </div>
      <div class="panel-card">
        <el-table :data="evaluations">
          <el-table-column label="指导教师" width="140">
            <template #default="{ row }">{{ row.teacher?.name || "-" }}</template>
          </el-table-column>
          <el-table-column label="维度评分" min-width="260">
            <template #default="{ row }">
              <el-space wrap>
                <el-tag v-for="item in row.dimensionScores || []" :key="`${row.id}-${item.key}`" type="success">{{ item.label }} {{ item.score }}</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column label="阶段评价" prop="stageComment" min-width="220" />
          <el-table-column label="总结评价" prop="summaryComment" min-width="260" />
          <el-table-column label="优势亮点" prop="strengthsComment" min-width="180" />
          <el-table-column label="改进建议" prop="improvementComment" min-width="180" />
          <el-table-column label="教师建议成绩" prop="recommendedScore" width="120" />
          <el-table-column label="最终成绩" prop="finalScore" width="120" />
          <el-table-column label="学院评语" prop="collegeComment" min-width="220" />
          <el-table-column label="学院确认状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.confirmedByCollege ? '已确认' : '待确认').type">{{ getStatusMeta(row.confirmedByCollege ? '已确认' : '待确认').label }}</el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无评价记录" />
          </template>
        </el-table>
      </div>
    </template>

    <el-dialog v-model="mentorDialogVisible" title="新建指导教师申请" width="520px">
      <el-form label-position="top">
        <el-form-item label="指导教师">
          <el-select v-model="mentorForm.teacherId" placeholder="请选择指导教师" style="width: 100%">
            <el-option v-for="item in teachers" :key="item.id" :label="`${item.name} / ${item.employeeNo}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生备注">
          <el-input v-model="mentorForm.studentRemark" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mentorDialogVisible = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitMentorApplication">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="internshipDialogVisible" title="新建实习申请" width="640px">
      <el-form label-position="top">
        <el-form-item label="实习单位">
          <el-select v-model="internshipForm.organizationId" placeholder="请选择实习单位" style="width: 100%">
            <el-option v-for="item in organizations" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="实习岗位">
              <el-input v-model="internshipForm.position" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年级目标">
              <el-input v-model="internshipForm.gradeTarget" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="14">
                    <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker
                v-model="internshipForm.startDate"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                placeholder="选择开始日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker
                v-model="internshipForm.endDate"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                placeholder="选择结束日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注说明">
          <el-input v-model="internshipForm.remark" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="申请附件">
          <div style="width: 100%">
            <input type="file" multiple @change="handleAttachmentChange($event, 'internship')" />
            <div class="subtle" style="margin-top: 8px">支持 pdf、doc、docx、xls、xlsx、jpg、jpeg、png、txt，单个附件不超过 10MB。</div>
            <div v-if="attachmentUploading.internship" class="subtle" style="margin-top: 8px">附件上传中...</div>
            <div v-if="internshipForm.attachments.length" style="margin-top: 12px; display: grid; gap: 8px">
              <div
                v-for="(item, index) in internshipForm.attachments"
                :key="item.id || item.storedName || `${item.name}-${index}`"
                style="display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border:1px solid rgba(15,118,110,0.14);border-radius:12px"
              >
                <div>
                  <div style="font-weight: 600">{{ item.name }}</div>
                  <div class="subtle">{{ formatFileSize(item.size) }}</div>
                </div>
                <el-space>
                  <el-button link type="primary" @click="handleDownloadAttachment(item)">下载</el-button>
                  <el-button link type="danger" @click="removeAttachment('internship', index)">删除</el-button>
                </el-space>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="internshipDialogVisible = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitInternshipApplication">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="formDialogVisible" :title="editingFormId ? (editingArchivedForm ? '修改表单' : '编辑表单') : '新建表单'" width="720px">
      <el-form label-position="top">
        <el-form-item label="表单模板">
          <el-select v-model="formModel.templateCode" placeholder="请选择表单模板" style="width: 100%" @change="handleTemplateChange">
            <el-option v-for="item in templates" :key="item.code" :label="`${item.name} (${item.category})`" :value="item.code" />
          </el-select>
        </el-form-item>
        <div v-if="selectedTemplate?.description" class="subtle" style="margin: -6px 0 14px">{{ selectedTemplate.description }}</div>
        <div v-if="editingArchivedForm" class="subtle" style="margin: -6px 0 14px">修改申请已通过，保存后可重新提交进入教师审核流程，原归档版本会保留在历史记录中。</div>
        <template v-for="field in templateFields" :key="field.key">
          <el-form-item :label="field.label">
            <el-input
              v-if="field.type === 'text'"
              v-model="formModel.content[field.key]"
              :placeholder="field.placeholder || `请输入${field.label}`"
            />
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="formModel.content[field.key]"
              type="textarea"
              :rows="5"
              :placeholder="field.placeholder || `请输入${field.label}`"
            />
            <el-input
              v-else
              v-model="formModel.content[field.key]"
              :placeholder="field.placeholder || 'YYYY-MM-DD'"
            />
          </el-form-item>
        </template>
        <el-form-item label="表单附件">
          <div style="width: 100%">
            <input type="file" multiple @change="handleAttachmentChange($event, 'form')" />
            <div class="subtle" style="margin-top: 8px">支持上传与本次表单相关的证明材料、图片或文档。</div>
            <div v-if="attachmentUploading.form" class="subtle" style="margin-top: 8px">附件上传中...</div>
            <div v-if="formModel.attachments.length" style="margin-top: 12px; display: grid; gap: 8px">
              <div
                v-for="(item, index) in formModel.attachments"
                :key="item.id || item.storedName || `${item.name}-${index}`"
                style="display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border:1px solid rgba(15,118,110,0.14);border-radius:12px"
              >
                <div>
                  <div style="font-weight: 600">{{ item.name }}</div>
                  <div class="subtle">{{ formatFileSize(item.size) }}</div>
                </div>
                <el-space>
                  <el-button link type="primary" @click="handleDownloadAttachment(item)">下载</el-button>
                  <el-button link type="danger" @click="removeAttachment('form', index)">删除</el-button>
                </el-space>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="提交方式">
          <el-switch v-model="formModel.submit" inline-prompt active-text="提交审核" inactive-text="保存草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitForm">{{ editingFormId ? "保存修改" : "提交表单" }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="modificationRequestDialogVisible" title="申请修改" width="560px">
      <el-form label-position="top">
        <el-form-item label="修改原因">
          <el-input v-model="modificationRequestForm.reason" type="textarea" :rows="5" placeholder="请说明需要修改的原因、修改点和补充内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modificationRequestDialogVisible = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitFormModificationRequest">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>


