<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { get, post, put } from "../api/http";

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
  content: {
    title: "",
    summary: "",
  },
  submit: true,
});

const formKeyword = ref("");
const messageKeyword = ref("");
const internshipKeyword = ref("");
const formPage = ref(1);
const internshipPage = ref(1);
const messagePage = ref(1);
const pageSize = 5;

const section = computed(() => route.meta.section || "dashboard");
const latestForms = computed(() => forms.value.slice(0, 5));
const unreadMessages = computed(() => messages.value.filter((item) => !item.read));

const filteredForms = computed(() => {
  const keyword = formKeyword.value.trim();
  if (!keyword) return forms.value;
  return forms.value.filter((item) =>
    [item.templateName, item.status, item.content?.title, item.content?.summary]
      .filter(Boolean)
      .some((value) => String(value).includes(keyword))
  );
});

const filteredInternships = computed(() => {
  const keyword = internshipKeyword.value.trim();
  if (!keyword) return internshipApplications.value;
  return internshipApplications.value.filter((item) =>
    [item.organization?.name, item.position, item.gradeTarget, item.status]
      .filter(Boolean)
      .some((value) => String(value).includes(keyword))
  );
});

const filteredMessages = computed(() => {
  const keyword = messageKeyword.value.trim();
  if (!keyword) return messages.value;
  return messages.value.filter((item) =>
    [item.type, item.title, item.content].filter(Boolean).some((value) => String(value).includes(keyword))
  );
});

const pagedForms = computed(() => filteredForms.value.slice((formPage.value - 1) * pageSize, formPage.value * pageSize));
const pagedInternships = computed(() =>
  filteredInternships.value.slice((internshipPage.value - 1) * pageSize, internshipPage.value * pageSize)
);
const pagedMessages = computed(() =>
  filteredMessages.value.slice((messagePage.value - 1) * pageSize, messagePage.value * pageSize)
);

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
  formModel.content.title = "";
  formModel.content.summary = "";
  formModel.submit = true;
}

async function submitMentorApplication() {
  if (!mentorForm.teacherId) {
    ElMessage.warning("请选择指导教师。");
    return;
  }

  try {
    await post("/mentor-applications", mentorForm);
    ElMessage.success("指导教师申请已提交");
    mentorDialogVisible.value = false;
    resetMentorForm();
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function submitInternshipApplication() {
  if (!internshipForm.organizationId || !internshipForm.position || !internshipForm.gradeTarget) {
    ElMessage.warning("请完整填写实习单位、岗位和年级。");
    return;
  }

  if (!internshipForm.startDate || !internshipForm.endDate) {
    ElMessage.warning("请填写开始和结束日期。");
    return;
  }

  try {
    await post("/internship-applications", internshipForm);
    ElMessage.success("实习申请已提交");
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
  formModel.content.title = row.content?.title || "";
  formModel.content.summary = row.content?.summary || "";
  formModel.submit = true;
  formDialogVisible.value = true;
}

async function submitForm() {
  if (!formModel.templateCode) {
    ElMessage.warning("请选择表单模板。");
    return;
  }

  if (!formModel.content.title.trim() || !formModel.content.summary.trim()) {
    ElMessage.warning("请填写表单标题和摘要。");
    return;
  }

  try {
    const payload = {
      templateCode: formModel.templateCode,
      content: {
        title: formModel.content.title,
        summary: formModel.content.summary,
      },
      submit: formModel.submit,
      attachments: [],
    };

    if (editingFormId.value) {
      await put(`/forms/${editingFormId.value}`, payload);
      ElMessage.success("表单已更新");
    } else {
      await post("/forms", payload);
      ElMessage.success("表单已创建");
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

watch([formKeyword, internshipKeyword, messageKeyword], () => {
  formPage.value = 1;
  internshipPage.value = 1;
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
          <h2>学生工作台</h2>
          <div class="subtle">查看指导关系、实习状态、待办任务与消息提醒。</div>
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
          <h2 style="font-size:20px">最近表单</h2>
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
          <h2 style="font-size:20px">未读消息</h2>
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
          <div class="subtle">选择实习单位并提交学院审批，学院可登记单位外部确认结果。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="internshipDialogVisible = true">新建申请</el-button>
      </div>
      <div class="panel-card">
        <div class="toolbar">
          <el-input v-model="internshipKeyword" placeholder="筛选单位、岗位、状态" clearable style="max-width: 320px" />
        </div>
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
        <el-pagination
          v-if="filteredInternships.length > pageSize"
          v-model:current-page="internshipPage"
          layout="prev, pager, next"
          :page-size="pageSize"
          :total="filteredInternships.length"
          style="margin-top: 16px; justify-content: flex-end"
        />
      </div>
    </template>

    <template v-else-if="section === 'forms'">
      <div class="page-header">
        <div>
          <h2>核心表单</h2>
          <div class="subtle">统一承载通用表单与任课、班主任核心表单，一期先走统一状态流转。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="formDialogVisible = true; resetFormModel()">新建表单</el-button>
      </div>
      <div class="panel-card">
        <div class="toolbar">
          <el-input v-model="formKeyword" placeholder="筛选表单、状态、标题" clearable style="max-width: 320px" />
        </div>
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
        <el-pagination
          v-if="filteredForms.length > pageSize"
          v-model:current-page="formPage"
          layout="prev, pager, next"
          :page-size="pageSize"
          :total="filteredForms.length"
          style="margin-top: 16px; justify-content: flex-end"
        />
      </div>
    </template>

    <template v-else-if="section === 'messages'">
      <div class="page-header">
        <div>
          <h2>消息中心</h2>
          <div class="subtle">一期包含待办提醒、审核结果与退回通知。</div>
        </div>
      </div>
      <div class="panel-card">
        <div class="toolbar">
          <el-input v-model="messageKeyword" placeholder="筛选类型、标题、内容" clearable style="max-width: 320px" />
        </div>
        <el-table :data="pagedMessages" style="margin-top: 16px">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ row.read ? "已读" : "未读" }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="markRead(row)">标记已读</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无消息" />
          </template>
        </el-table>
        <el-pagination
          v-if="filteredMessages.length > pageSize"
          v-model:current-page="messagePage"
          layout="prev, pager, next"
          :page-size="pageSize"
          :total="filteredMessages.length"
          style="margin-top: 16px; justify-content: flex-end"
        />
      </div>
    </template>

    <template v-else-if="section === 'results'">
      <div class="page-header">
        <div>
          <h2>结果查看</h2>
          <div class="subtle">查看指导教师评价与学院最终确认结果。</div>
        </div>
      </div>
      <div class="panel-card">
        <el-table :data="evaluations">
          <el-table-column label="阶段评价" prop="stageComment" min-width="220" />
          <el-table-column label="总结评价" prop="summaryComment" min-width="260" />
          <el-table-column label="最终成绩" prop="finalScore" width="120" />
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
          <el-select v-model="mentorForm.teacherId" placeholder="请选择指导教师" style="width:100%">
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
          <el-select v-model="internshipForm.organizationId" placeholder="请选择实习单位" style="width:100%">
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

    <el-dialog v-model="formDialogVisible" :title="editingFormId ? '编辑表单' : '新建表单'" width="640px">
      <el-form label-position="top">
        <el-form-item label="表单模板">
          <el-select v-model="formModel.templateCode" placeholder="请选择模板" style="width:100%">
            <el-option v-for="item in templates" :key="item.code" :label="`${item.name} (${item.category})`" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="formModel.content.title" />
        </el-form-item>
        <el-form-item label="内容摘要">
          <el-input v-model="formModel.content.summary" type="textarea" :rows="5" />
        </el-form-item>
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
