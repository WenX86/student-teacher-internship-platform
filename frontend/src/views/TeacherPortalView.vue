<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { downloadFile, get, post } from "../api/http";
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";
import { getMessageTypeMeta, getReadStatusMeta, getStatusMeta } from "../utils/status";

const route = useRoute();
const loading = ref(false);
const dashboard = ref({});
const mentorApplications = ref([]);
const forms = ref([]);
const guidanceRecords = ref([]);
const evaluations = ref([]);
const messages = ref([]);
const alerts = ref([]);

const mentorReviewDialog = ref(false);
const formReviewDialog = ref(false);
const guidanceDialog = ref(false);
const evaluationDialog = ref(false);
const currentRow = ref(null);

const mentorReviewForm = reactive({ approved: true, comment: "" });
const formReviewForm = reactive({ approved: true, score: 90, comment: "" });
const guidanceForm = reactive({
  studentId: "",
  guidanceAt: "",
  mode: "线上指导",
  problem: "",
  advice: "",
  followUp: "",
});
const evaluationForm = reactive({
  studentId: "",
  summaryComment: "",
  finalScore: 90,
  dimensionScores: [],
});

const pageSize = 5;
const messagePageSize = 10;

const section = computed(() => route.meta.section || "dashboard");
const activeStudents = computed(() => mentorApplications.value.filter((item) => item.status === "已生效").map((item) => item.student));
const pendingMentorApplications = computed(() => mentorApplications.value.filter((item) => item.status === "待教师确认"));
const reviewableForms = computed(() => forms.value.filter((item) => item.status === "教师审核中"));
const unreadTeacherMessages = computed(() => messages.value.filter((item) => !item.read));
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

const {
  keyword: reviewKeyword,
  currentPage: reviewPage,
  filteredItems: filteredReviewForms,
  pagedItems: pagedReviewForms,
} = useFilteredPagination({
  source: reviewableForms,
  matcher: (item) => [item.studentName, item.templateName, item.content?.summary],
  pageSize,
});

const {
  keyword: guidanceKeyword,
  currentPage: guidancePage,
  filteredItems: filteredGuidanceRecords,
  pagedItems: pagedGuidanceRecords,
} = useFilteredPagination({
  source: guidanceRecords,
  matcher: (item) => [item.student?.name, item.problem, item.advice, item.followUp],
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

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const pad = (num) => String(num).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
    const [dashboardData, mentorData, formData, guidanceData, evaluationData, messageData, alertData] = await Promise.all([
      get("/dashboard"),
      get("/mentor-applications"),
      get("/forms"),
      get("/guidance-records"),
      get("/evaluations"),
      get("/messages"),
      get("/risk-alerts"),
    ]);
    dashboard.value = dashboardData;
    mentorApplications.value = mentorData;
    forms.value = formData;
    guidanceRecords.value = guidanceData;
    evaluations.value = evaluationData;
    messages.value = messageData;
    alerts.value = alertData;
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}

function openMentorReview(row) {
  currentRow.value = row;
  mentorReviewForm.approved = true;
  mentorReviewForm.comment = "";
  mentorReviewDialog.value = true;
}

async function submitMentorReview() {
  try {
    await post(`/mentor-applications/${currentRow.value.id}/teacher-review`, mentorReviewForm);
    ElMessage.success("指导申请处理成功");
    mentorReviewDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function openFormReview(row) {
  currentRow.value = row;
  formReviewForm.approved = true;
  formReviewForm.score = row.score || 90;
  formReviewForm.comment = "";
  formReviewDialog.value = true;
}

async function submitFormReview() {
  if (formReviewForm.score < 0 || formReviewForm.score > 100) {
    ElMessage.warning("评分应在 0 到 100 之间");
    return;
  }
  try {
    await post(`/forms/${currentRow.value.id}/teacher-review`, formReviewForm);
    ElMessage.success("表单审核已提交");
    formReviewDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function submitGuidanceRecord() {
  if (!guidanceForm.studentId || !guidanceForm.guidanceAt) {
    ElMessage.warning("请先选择学生并填写指导时间");
    return;
  }
  if (!guidanceForm.problem.trim() || !guidanceForm.advice.trim()) {
    ElMessage.warning("请填写问题描述和指导建议");
    return;
  }
  try {
    await post("/guidance-records", guidanceForm);
    ElMessage.success("指导记录已保存");
    guidanceDialog.value = false;
    guidanceForm.studentId = "";
    guidanceForm.guidanceAt = "";
    guidanceForm.mode = "线上指导";
    guidanceForm.problem = "";
    guidanceForm.advice = "";
    guidanceForm.followUp = "";
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
function createDefaultEvaluationDimensions(studentId = "") {
  return buildEvaluationDimensions(studentId);
}

function getProcessScore(studentId) {
  const scores = forms.value
    .filter((item) => item.studentId === studentId && typeof item.score === "number")
    .map((item) => Number(item.score))
    .filter((item) => Number.isFinite(item));
  if (!scores.length) {
    return 0;
  }
  return Math.round(scores.reduce((total, score) => total + score, 0) / scores.length);
}

function normalizeEvaluationDimension(item, studentId) {
  const key = item?.key === "management" ? "process" : item?.key || "";
  const labelMap = {
    ethics: "师德表现",
    teaching: "教学能力",
    process: "过程评分",
    reflection: "教学反思",
  };
  const score = key === "process" ? getProcessScore(studentId) : Number(item?.score ?? 0);
  return {
    key,
    label: item?.label || labelMap[key] || key,
    score: Number.isFinite(score) ? score : 0,
    comment: item?.comment || "",
  };
}

function buildEvaluationDimensions(studentId, sourceDimensions = []) {
  const normalizedSource = sourceDimensions.map((item) => normalizeEvaluationDimension(item, studentId));
  const dimensionMap = new Map(normalizedSource.map((item) => [item.key, item]));
  return [
    dimensionMap.get("ethics") || { key: "ethics", label: "师德表现", score: 90, comment: "" },
    dimensionMap.get("teaching") || { key: "teaching", label: "教学能力", score: 90, comment: "" },
    { key: "process", label: "过程评分", score: getProcessScore(studentId), comment: dimensionMap.get("process")?.comment || "" },
    dimensionMap.get("reflection") || { key: "reflection", label: "教学反思", score: 90, comment: "" },
  ];
}

function calculateEvaluationFinalScore(dimensions) {
  const scoreMap = new Map((dimensions || []).map((item) => [item.key, Number(item.score) || 0]));
  return Math.round(
    (scoreMap.get("ethics") || 0) * 0.2
      + (scoreMap.get("teaching") || 0) * 0.2
      + (scoreMap.get("process") || 0) * 0.4
      + (scoreMap.get("reflection") || 0) * 0.2,
  );
}

watch(
  () => evaluationForm.studentId,
  (studentId) => {
    if (!evaluationDialog.value || !studentId) {
      return;
    }
    evaluationForm.dimensionScores = buildEvaluationDimensions(studentId, evaluationForm.dimensionScores);
    evaluationForm.finalScore = calculateEvaluationFinalScore(evaluationForm.dimensionScores);
  },
);

watch(
  () => evaluationForm.dimensionScores,
  () => {
    evaluationForm.finalScore = calculateEvaluationFinalScore(evaluationForm.dimensionScores);
  },
  { deep: true },
);

function canEditEvaluation(row) {
  return !row?.confirmedByCollege;
}
function openEvaluationDialog(row) {
  if (row && !canEditEvaluation(row)) {
    ElMessage.warning("学院已确认评价，当前记录不可再编辑");
    return;
  }
  evaluationForm.studentId = row?.student?.id || row?.id || "";
  evaluationForm.summaryComment = row?.summaryComment || "";
  evaluationForm.dimensionScores = buildEvaluationDimensions(evaluationForm.studentId, row?.dimensionScores?.length ? row.dimensionScores : []);
  evaluationForm.finalScore = calculateEvaluationFinalScore(evaluationForm.dimensionScores);
  evaluationDialog.value = true;
}

async function submitEvaluation() {
  if (!evaluationForm.studentId) {
    ElMessage.warning("请先选择学生");
    return;
  }
  if (evaluationForm.finalScore < 0 || evaluationForm.finalScore > 100) {
    ElMessage.warning("最终成绩应在 0 到 100 之间");
    return;
  }
  for (const item of evaluationForm.dimensionScores) {
    if (item.score < 0 || item.score > 100) {
      ElMessage.warning(`${item.label} 评分应在 0 到 100 之间`);
      return;
    }
  }
  try {
    await post("/evaluations", evaluationForm);
    ElMessage.success("评价提交成功");
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
          <h2>教师工作台</h2>
          <div class="subtle">查看待确认指导申请、待审核材料，以及负责学生的整体进度。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>负责学生数</h4><strong>{{ dashboard.studentCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待确认指导申请</h4><strong>{{ dashboard.pendingMentorRequests || 0 }}</strong></div>
        <div class="metric-card"><h4>待审核材料</h4><strong>{{ dashboard.pendingReviewCount || 0 }}</strong></div>
        <div class="metric-card"><h4>材料完成率</h4><strong>{{ dashboard.completionRate || 0 }}%</strong></div>
      </div>
      <div class="panel-card">
        <div class="page-header"><h2 style="font-size: 20px">已生效学生名单</h2></div>
        <el-table :data="activeStudents" style="margin-top: 16px">
          <el-table-column prop="name" label="学生姓名" />
          <el-table-column prop="studentNo" label="学号" />
          <el-table-column prop="internshipType" label="实习类型" />
          <el-table-column label="实习状态">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.internshipStatus).type">{{ getStatusMeta(row.internshipStatus).label }}</el-tag>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无学生数据" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'mentor'">
      <div class="page-header">
        <div>
          <h2>待确认指导申请</h2>
          <div class="subtle">教师可确认或驳回申请，最终仍需学院复核后生效。</div>
        </div>
      </div>
      <div class="panel-card">
        <el-table :data="pendingMentorApplications">
          <el-table-column label="学生" min-width="160">
            <template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template>
          </el-table-column>
          <el-table-column label="学生备注" min-width="220">
            <template #default="{ row }"><span :class="row.studentRemark ? '' : 'subtle'">{{ row.studentRemark || "未填写备注" }}</span></template>
          </el-table-column>
          <el-table-column label="申请时间" width="180">
            <template #default="{ row }"><span class="table-datetime">{{ formatDateTime(row.createdAt) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }"><el-button link type="primary" @click="openMentorReview(row)">处理申请</el-button></template>
          </el-table-column>
          <template #empty><el-empty description="暂无待确认申请" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'reviews'">
      <div class="page-header">
        <div>
          <h2>材料审核</h2>
          <div class="subtle">学生表单统一进入教师审核流程，审核通过后直接归档。</div>
        </div>
      </div>
      <FilterTablePanel
        v-model:keyword="reviewKeyword"
        v-model:current-page="reviewPage"
        placeholder="搜索学生、表单或摘要"
        :total="filteredReviewForms.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedReviewForms" style="margin-top: 16px">
          <el-table-column prop="studentName" label="学生姓名" width="120" />
          <el-table-column prop="templateName" label="模板名称" min-width="150" />
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.status).type">{{ getStatusMeta(row.status).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="内容摘要" min-width="220">
            <template #default="{ row }">{{ row.content?.summary }}</template>
          </el-table-column>
          <el-table-column label="附件列表" min-width="220">
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
              <span v-else class="subtle">无附件</span>
            </template>
          </el-table-column>
          <el-table-column label="审核操作" width="120">
            <template #default="{ row }"><el-button link type="primary" @click="openFormReview(row)">审核表单</el-button></template>
          </el-table-column>
          <template #empty><el-empty description="暂无待审核表单" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'guidance'">
      <div class="page-header">
        <div>
          <h2>指导记录</h2>
          <div class="subtle">记录线上线下指导情况、问题分析和整改建议。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="guidanceDialog = true">新建指导记录</el-button>
      </div>
      <FilterTablePanel
        v-model:keyword="guidanceKeyword"
        v-model:current-page="guidancePage"
        placeholder="搜索学生、问题或建议"
        :total="filteredGuidanceRecords.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedGuidanceRecords" style="margin-top: 16px">
          <el-table-column label="学生" min-width="120"><template #default="{ row }">{{ row.student?.name }}</template></el-table-column>
          <el-table-column prop="guidanceAt" label="指导时间" width="180" />
          <el-table-column prop="mode" label="指导方式" width="100" />
          <el-table-column prop="problem" label="问题描述" min-width="160" />
          <el-table-column prop="advice" label="指导建议" min-width="180" />
          <el-table-column prop="followUp" label="后续跟进" min-width="180" />
          <template #empty><el-empty description="暂无指导记录" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'evaluations'">
      <div class="page-header">
        <div>
          <h2>评价管理</h2>
          <div class="subtle">填写多维评价和总结评价，并提交学院确认最终成绩。</div>
        </div>
        <el-button type="primary" color="#0f766e" :disabled="!activeStudents.length" @click="openEvaluationDialog(activeStudents[0])">新建评价</el-button>
      </div>
      <div class="panel-card">
        <el-table :data="evaluations">
          <el-table-column label="学生" width="120"><template #default="{ row }">{{ row.student?.name }}</template></el-table-column>
          <el-table-column label="维度评分" min-width="260">
            <template #default="{ row }">
              <el-space wrap>
                <el-tag v-for="item in row.dimensionScores || []" :key="`${row.id}-${item.key}`" type="success">{{ item.label }} {{ item.score }}</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column prop="summaryComment" label="总结评价" min-width="240" />
          <el-table-column prop="finalScore" label="总评分" width="100" />
          <el-table-column label="学院确认状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusMeta(row.confirmedByCollege ? '已确认' : '待确认').type">{{ getStatusMeta(row.confirmedByCollege ? '已确认' : '待确认').label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="canEditEvaluation(row)" link type="primary" @click="openEvaluationDialog(row)">编辑评价</el-button>
              <span v-else class="subtle">已确认</span>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无评价记录" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'alerts'">
      <div class="page-header">
        <div>
          <h2>风险提醒</h2>
          <div class="subtle">查看超时审核、退回未改和待学院确认的风险任务，并一键发送催办提醒。</div>
        </div>
      </div>
      <FilterTablePanel
        v-model:keyword="alertKeyword"
        v-model:current-page="alertPage"
        placeholder="搜索类别、内容或对象"
        :total="filteredAlerts.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedAlerts" style="margin-top: 16px">
          <el-table-column prop="category" label="类别" width="120" />
          <el-table-column label="等级" width="100">
            <template #default="{ row }"><el-tag :type="row.level === 'danger' ? 'danger' : 'warning'">{{ row.level === 'danger' ? '高风险' : '提醒' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column prop="targetName" label="对象" width="120" />
          <el-table-column prop="overdueDays" label="逾期天数" width="100" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button v-if="row.remindable" link type="primary" @click="sendReminder(row)">{{ row.remindActionLabel || '发送提醒' }}</el-button>
              <span v-else class="subtle">请尽快处理</span>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无风险提醒" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'messages'">
      <div class="page-header">
        <div>
          <h2>消息中心</h2>
          <div class="subtle">聚合审核待办、学生提醒和系统结果反馈。</div>
        </div>
        <el-tag type="warning">未读 {{ unreadTeacherMessages.length }} 条</el-tag>
      </div>
      <FilterTablePanel
        v-model:keyword="messageKeyword"
        v-model:current-page="messagePage"
        placeholder="筛选类型、标题或内容"
        :total="filteredMessages.length"
        :page-size="messagePageSize"
      >
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
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="content" label="内容" min-width="240" />
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
          <template #empty><el-empty description="暂无消息" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <el-dialog v-model="mentorReviewDialog" title="处理指导申请" width="520px">
      <el-form label-position="top">
        <el-form-item label="审核结果"><el-switch v-model="mentorReviewForm.approved" inline-prompt active-text="通过" inactive-text="驳回" /></el-form-item>
        <el-form-item label="审核意见"><el-input v-model="mentorReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mentorReviewDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitMentorReview">提交审核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="formReviewDialog" title="审核表单" width="560px">
      <el-form label-position="top">
        <el-form-item v-if="attachmentList(currentRow).length" label="附件列表">
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
        <el-form-item label="审核结果"><el-switch v-model="formReviewForm.approved" inline-prompt active-text="通过" inactive-text="退回" /></el-form-item>
        <el-form-item label="评分"><el-input-number v-model="formReviewForm.score" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="审核意见"><el-input v-model="formReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formReviewDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitFormReview">提交审核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="guidanceDialog" title="新建指导记录" width="640px">
      <el-form label-position="top">
        <el-form-item label="学生">
          <el-select v-model="guidanceForm.studentId" placeholder="请选择学生" style="width: 100%">
            <el-option v-for="item in activeStudents" :key="item.id" :label="`${item.name} / ${item.studentNo}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="指导时间"><el-input v-model="guidanceForm.guidanceAt" placeholder="YYYY-MM-DD HH:mm" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="指导方式">
              <el-select v-model="guidanceForm.mode" style="width: 100%">
                <el-option label="线上指导" value="线上指导" />
                <el-option label="线下指导" value="线下指导" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="问题描述"><el-input v-model="guidanceForm.problem" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="指导建议"><el-input v-model="guidanceForm.advice" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="后续跟进"><el-input v-model="guidanceForm.followUp" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="guidanceDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitGuidanceRecord">保存记录</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evaluationDialog" title="新建评价" width="720px">
      <el-form label-position="top">
        <el-form-item label="学生">
          <el-select v-model="evaluationForm.studentId" placeholder="请选择学生" style="width: 100%">
            <el-option v-for="item in activeStudents" :key="item.id" :label="`${item.name} / ${item.studentNo}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <div v-for="item in evaluationForm.dimensionScores" :key="item.key" class="evaluation-card">
          <div class="evaluation-card-head">
            <div class="evaluation-card-label">{{ item.label }}</div>
            <div class="evaluation-score-wrap">
              <el-input-number v-model="item.score" :min="0" :max="100" :disabled="item.key === 'process'" class="evaluation-score" />
              <div v-if="item.key === 'process'" class="evaluation-note">过程评分由学生表单得分平均值自动生成</div>
            </div>
          </div>
        </div>
        <el-form-item label="总结评价" class="evaluation-summary-item">
          <el-input v-model="evaluationForm.summaryComment" type="textarea" :rows="5" placeholder="请填写对该学生本次实习的总体评价" />
        </el-form-item>
        <el-form-item label="总评分">
          <el-input-number v-model="evaluationForm.finalScore" :min="0" :max="100" style="width: 100%" disabled />
        </el-form-item>
        <div class="evaluation-form-note">总评分 = 师德表现 20% + 教学能力 20% + 过程评分 40% + 教学反思 20%</div>
      </el-form>
      <template #footer>
        <el-button @click="evaluationDialog = false">取消</el-button>
        <el-button type="primary" color="#0f766e" @click="submitEvaluation">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.evaluation-card {
  margin-bottom: 12px;
  padding: 16px 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.98));
  border: 1px solid rgba(15, 118, 110, 0.08);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.evaluation-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.evaluation-card-label {
  min-width: 0;
  flex: 1;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
  color: #334155;
}

.evaluation-score-wrap {
  min-width: 240px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.evaluation-score {
  width: 100%;
}

.evaluation-note {
  max-width: 240px;
  text-align: right;
  font-size: 12px;
  line-height: 1.45;
  color: #64748b;
}

.evaluation-summary-item :deep(.el-form-item__label),
.evaluation-form-note {
  font-size: 14px;
  color: #64748b;
}

.evaluation-summary-item {
  margin-top: 6px;
}

.table-datetime {
  display: inline-block;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.evaluation-form-note {
  margin-top: -8px;
  margin-bottom: 8px;
  line-height: 1.5;
}
</style>


