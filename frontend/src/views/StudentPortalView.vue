<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { get, post, put } from "../api/http";
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";

const route = useRoute();
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

const mentorDialogVisible = ref(false);
const internshipDialogVisible = ref(false);
const formDialogVisible = ref(false);
const editingFormId = ref("");

const mentorForm = reactive({
  teacherId: "",
  studentRemark: "",
});

const internshipForm = reactive({
  organizationId: "",
  batchName: "2026春季批次",
  position: "",
  gradeTarget: "",
  startDate: "",
  endDate: "",
  remark: "",
});

const formModel = reactive({
  templateCode: "",
  content: {},
  submit: true,
});

const pageSize = 5;

const section = computed(() => route.meta.section || "dashboard");
const latestForms = computed(() => forms.value.slice(0, 5));
const unreadMessages = computed(() => messages.value.filter((item) => !item.read));
const selectedTemplate = computed(() => templates.value.find((item) => item.code === formModel.templateCode) || null);
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
  source: messages,
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

function resetMentorForm() {
  mentorForm.teacherId = "";
  mentorForm.studentRemark = "";
}

function resetInternshipForm() {
  internshipForm.organizationId = "";
  internshipForm.batchName = "2026春季批次";
  internshipForm.position = "";
  internshipForm.gradeTarget = "";
  internshipForm.startDate = "";
  internshipForm.endDate = "";
  internshipForm.remark = "";
}

function resetFormModel() {
  editingFormId.value = "";
  formModel.templateCode = "";
  syncFormContent([]);
  formModel.submit = true;
}

function createDefaultFields() {
  return [
    { key: "title", label: "标题", type: "text", required: true, placeholder: "请输入标题" },
    { key: "summary", label: "内容摘要", type: "textarea", required: true, placeholder: "请输入内容摘要" },
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

async function submitMentorApplication() {
  if (!mentorForm.teacherId) {
    ElMessage.warning("请选择指导教师。");
    return;
  }

  try {
    await post("/mentor-applications", mentorForm);
    ElMessage.success("指导教师申请已提交。");
    mentorDialogVisible.value = false;
    resetMentorForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function submitInternshipApplication() {
  if (!internshipForm.organizationId || !internshipForm.position || !internshipForm.gradeTarget) {
    ElMessage.warning("请完整填写实习单位、岗位和年级信息。");
    return;
  }

  if (!internshipForm.startDate || !internshipForm.endDate) {
    ElMessage.warning("请填写开始和结束日期。");
    return;
  }

  try {
    await post("/internship-applications", internshipForm);
    ElMessage.success("实习申请已提交。");
    internshipDialogVisible.value = false;
    resetInternshipForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function editExistingForm(row) {
  editingFormId.value = row.id;
  formModel.templateCode = row.templateCode;
  const template = templates.value.find((item) => item.code === row.templateCode);
  syncFormContent(template?.fieldSchema || createDefaultFields(), row.content || {});
  formModel.submit = true;
  formDialogVisible.value = true;
}

async function submitForm() {
  if (!formModel.templateCode) {
    ElMessage.warning("请选择表单模板。");
    return;
  }

  for (const field of templateFields.value) {
    const value = `${formModel.content[field.key] ?? ""}`.trim();
    if (field.required && !value) {
      ElMessage.warning(`请填写${field.label}。`);
      return;
    }
  }

  try {
    const payload = {
      templateCode: formModel.templateCode,
      content: { ...formModel.content },
      submit: formModel.submit,
      attachments: [],
    };

    if (editingFormId.value) {
      await put(`/forms/${editingFormId.value}`, payload);
      ElMessage.success("表单已更新。");
    } else {
      await post("/forms", payload);
      ElMessage.success("表单已创建。");
    }

    formDialogVisible.value = false;
    resetFormModel();
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
          <h2>学生工作台</h2>
          <div class="subtle">查看指导关系、实习状态、待办任务和消息提醒。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>当前实习状态</h4><strong>{{ dashboard.internshipStatus || "待完善" }}</strong></div>
        <div class="metric-card"><h4>指导关系状态</h4><strong>{{ dashboard.mentorStatus || "未申请" }}</strong></div>
        <div class="metric-card"><h4>待办任务</h4><strong>{{ dashboard.todoCount || 0 }}</strong></div>
        <div class="metric-card"><h4>已归档表单</h4><strong>{{ dashboard.archivedCount || 0 }}</strong></div>
      </div>

      <div class="panel-card">
        <div class="page-header">
          <h2 style="font-size: 20px">最近表单</h2>
          <el-button type="primary" color="#0f766e" @click="formDialogVisible = true; resetFormModel()">新建表单</el-button>
        </div>
        <el-table :data="latestForms" style="margin-top: 16px">
          <el-table-column prop="templateName" label="表单" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="updatedAt" label="更新时间" />
          <template #empty>
            <el-empty description="暂无最近表单" />
          </template>
        </el-table>
      </div>

      <div class="panel-card">
        <div class="page-header">
          <h2 style="font-size: 20px">未读消息</h2>
          <el-tag type="warning">{{ unreadMessages.length }} 条</el-tag>
        </div>
        <el-table :data="unreadMessages" style="margin-top: 16px">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="createdAt" label="时间" width="200" />
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
          <div class="subtle">选择指导教师后发起申请，待教师确认并由学院复核生效。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="mentorDialogVisible = true">发起申请</el-button>
      </div>
      <div class="panel-card">
        <el-table :data="mentorApplications">
          <el-table-column label="教师" min-width="160">
            <template #default="{ row }">{{ row.teacher?.name }} / {{ row.teacher?.employeeNo }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column prop="studentRemark" label="申请说明" min-width="200" />
          <el-table-column prop="teacherRemark" label="教师意见" min-width="160" />
          <el-table-column prop="collegeRemark" label="学院意见" min-width="160" />
          <template #empty>
            <el-empty description="暂无指导教师申请记录" />
          </template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'internship'">
      <div class="page-header">
        <div>
          <h2>实习申请</h2>
          <div class="subtle">选择实习单位并提交学院审批，学院会同步登记单位确认结果。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="internshipDialogVisible = true">新建申请</el-button>
      </div>
      <FilterTablePanel
        v-model:keyword="internshipKeyword"
        v-model:current-page="internshipPage"
        placeholder="筛选单位、岗位、状态"
        :total="filteredInternships.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedInternships" style="margin-top: 16px">
          <el-table-column label="实习单位" min-width="180">
            <template #default="{ row }">{{ row.organization?.name }}</template>
          </el-table-column>
          <el-table-column prop="position" label="岗位" width="140" />
          <el-table-column prop="gradeTarget" label="年级" width="120" />
          <el-table-column prop="status" label="审批状态" width="140" />
          <el-table-column prop="organizationConfirmation" label="单位确认" width="140" />
          <el-table-column prop="reviewComment" label="学院意见" min-width="200" />
          <template #empty>
            <el-empty description="暂无实习申请记录" />
          </template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'forms'">
      <div class="page-header">
        <div>
          <h2>核心表单</h2>
          <div class="subtle">统一承载通用表单以及任课、班主任实习核心表单，按业务流程逐级流转。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="formDialogVisible = true; resetFormModel()">新建表单</el-button>
      </div>
      <FilterTablePanel
        v-model:keyword="formKeyword"
        v-model:current-page="formPage"
        placeholder="筛选表单、状态、标题"
        :total="filteredForms.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedForms" style="margin-top: 16px">
          <el-table-column prop="templateName" label="表单名称" min-width="160" />
          <el-table-column prop="category" label="类别" width="120" />
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column prop="version" label="版本" width="90" />
          <el-table-column label="标题" min-width="180">
            <template #default="{ row }">{{ row.content?.title }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="editExistingForm(row)">编辑</el-button>
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
      </div>
      <FilterTablePanel
        v-model:keyword="messageKeyword"
        v-model:current-page="messagePage"
        placeholder="筛选类型、标题、内容"
        :total="filteredMessages.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedMessages" style="margin-top: 16px">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ row.read ? "已读" : "未读" }}</template>
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
          <el-table-column label="评价维度" min-width="260">
            <template #default="{ row }">
              <el-space wrap>
                <el-tag v-for="item in row.dimensionScores || []" :key="`${row.id}-${item.key}`" type="success">{{ item.label }} {{ item.score }}</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column label="阶段评价" prop="stageComment" min-width="220" />
          <el-table-column label="总结评价" prop="summaryComment" min-width="260" />
          <el-table-column label="优点亮点" prop="strengthsComment" min-width="180" />
          <el-table-column label="改进建议" prop="improvementComment" min-width="180" />
          <el-table-column label="教师建议成绩" prop="recommendedScore" width="120" />
          <el-table-column label="最终成绩" prop="finalScore" width="120" />
          <el-table-column label="学院意见" prop="collegeComment" min-width="220" />
          <el-table-column label="学院确认" width="120">
            <template #default="{ row }">{{ row.confirmedByCollege ? "已确认" : "待确认" }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无评价结果" />
          </template>
        </el-table>
      </div>
    </template>

    <el-dialog v-model="mentorDialogVisible" title="发起指导教师申请" width="520px">
      <el-form label-position="top">
        <el-form-item label="选择教师">
          <el-select v-model="mentorForm.teacherId" placeholder="请选择指导教师" style="width: 100%">
            <el-option v-for="item in teachers" :key="item.id" :label="`${item.name} / ${item.employeeNo}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请说明">
          <el-input v-model="mentorForm.studentRemark" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mentorDialogVisible = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitMentorApplication">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="internshipDialogVisible" title="提交实习申请" width="640px">
      <el-form label-position="top">
        <el-form-item label="实习单位">
          <el-select v-model="internshipForm.organizationId" placeholder="请选择实习单位" style="width: 100%">
            <el-option v-for="item in organizations" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-input v-model="internshipForm.position" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年级/对象">
              <el-input v-model="internshipForm.gradeTarget" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-input v-model="internshipForm.startDate" placeholder="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-input v-model="internshipForm.endDate" placeholder="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="申请说明">
          <el-input v-model="internshipForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="internshipDialogVisible = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitInternshipApplication">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="formDialogVisible" :title="editingFormId ? '编辑表单' : '新建表单'" width="720px">
      <el-form label-position="top">
        <el-form-item label="表单模板">
          <el-select v-model="formModel.templateCode" placeholder="请选择模板" style="width: 100%" @change="handleTemplateChange">
            <el-option v-for="item in templates" :key="item.code" :label="`${item.name} (${item.category})`" :value="item.code" />
          </el-select>
        </el-form-item>
        <div v-if="selectedTemplate?.description" class="subtle" style="margin: -6px 0 14px">{{ selectedTemplate.description }}</div>
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
        <el-form-item label="提交方式">
          <el-switch v-model="formModel.submit" inline-prompt active-text="提交审核" inactive-text="保存草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitForm">{{ editingFormId ? "保存" : "创建" }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>
