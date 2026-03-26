<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { get, post } from "../api/http";
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";

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
  mode: "线上",
  problem: "",
  advice: "",
  followUp: "",
});
const evaluationForm = reactive({
  studentId: "",
  stageComment: "",
  summaryComment: "",
  finalScore: 90,
  dimensionScores: [],
  strengthsComment: "",
  improvementComment: "",
});

const pageSize = 5;

const section = computed(() => route.meta.section || "dashboard");
const activeStudents = computed(() => mentorApplications.value.filter((item) => item.status === "已生效").map((item) => item.student));
const pendingMentorApplications = computed(() => mentorApplications.value.filter((item) => item.status === "待教师确认"));
const reviewableForms = computed(() => forms.value.filter((item) => item.status === "教师审核中"));

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
  source: messages,
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
    ElMessage.success("指导申请处理完成。");
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
    ElMessage.warning("评分范围应在 0 到 100 之间。");
    return;
  }
  try {
    await post(`/forms/${currentRow.value.id}/teacher-review`, formReviewForm);
    ElMessage.success("表单审核完成。");
    formReviewDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function submitGuidanceRecord() {
  if (!guidanceForm.studentId || !guidanceForm.guidanceAt) {
    ElMessage.warning("请选择学生并填写指导时间。");
    return;
  }
  if (!guidanceForm.problem.trim() || !guidanceForm.advice.trim()) {
    ElMessage.warning("请至少填写学生问题和整改建议。");
    return;
  }
  try {
    await post("/guidance-records", guidanceForm);
    ElMessage.success("指导记录已保存。");
    guidanceDialog.value = false;
    guidanceForm.studentId = "";
    guidanceForm.guidanceAt = "";
    guidanceForm.mode = "线上";
    guidanceForm.problem = "";
    guidanceForm.advice = "";
    guidanceForm.followUp = "";
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function createDefaultEvaluationDimensions() {
  return [
    { key: "ethics", label: "职业素养", score: 90, comment: "" },
    { key: "teaching", label: "教学实施", score: 90, comment: "" },
    { key: "management", label: "班级管理", score: 90, comment: "" },
    { key: "reflection", label: "反思改进", score: 90, comment: "" },
  ];
}

function openEvaluationDialog(row) {
  evaluationForm.studentId = row?.student?.id || row?.id || "";
  evaluationForm.stageComment = row?.stageComment || "";
  evaluationForm.summaryComment = row?.summaryComment || "";
  evaluationForm.finalScore = row?.recommendedScore || row?.finalScore || 90;
  evaluationForm.dimensionScores = (row?.dimensionScores?.length ? row.dimensionScores : createDefaultEvaluationDimensions()).map((item) => ({ ...item }));
  evaluationForm.strengthsComment = row?.strengthsComment || "";
  evaluationForm.improvementComment = row?.improvementComment || "";
  evaluationDialog.value = true;
}

async function submitEvaluation() {
  if (!evaluationForm.studentId) {
    ElMessage.warning("请选择学生。");
    return;
  }
  if (evaluationForm.finalScore < 0 || evaluationForm.finalScore > 100) {
    ElMessage.warning("建议成绩需在 0 到 100 之间。");
    return;
  }
  for (const item of evaluationForm.dimensionScores) {
    if (item.score < 0 || item.score > 100) {
      ElMessage.warning(`${item.label}分数需在 0 到 100 之间。`);
      return;
    }
  }
  try {
    await post("/evaluations", evaluationForm);
    ElMessage.success("评价已提交学院确认。");
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
          <h2>教师工作台</h2>
          <div class="subtle">查看待确认指导申请、待审核材料和负责学生的整体进度。</div>
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
          <el-table-column prop="name" label="学生" />
          <el-table-column prop="studentNo" label="学号" />
          <el-table-column prop="internshipType" label="实习类型" />
          <el-table-column prop="internshipStatus" label="实习状态" />
          <template #empty><el-empty description="暂无已生效指导学生" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'mentor'">
      <div class="page-header">
        <div>
          <h2>待确认指导申请</h2>
          <div class="subtle">教师可确认或驳回申请，最终仍需学院复核生效。</div>
        </div>
      </div>
      <div class="panel-card">
        <el-table :data="pendingMentorApplications">
          <el-table-column label="学生" min-width="160">
            <template #default="{ row }">{{ row.student?.name }} / {{ row.student?.studentNo }}</template>
          </el-table-column>
          <el-table-column prop="studentRemark" label="申请说明" min-width="220" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }"><el-button link type="primary" @click="openMentorReview(row)">处理</el-button></template>
          </el-table-column>
          <template #empty><el-empty description="暂无待确认指导申请" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'reviews'">
      <div class="page-header">
        <div>
          <h2>材料审核</h2>
          <div class="subtle">学生表单统一进入教师审核链路，审核通过后再流转学院归档。</div>
        </div>
      </div>
      <FilterTablePanel
        v-model:keyword="reviewKeyword"
        v-model:current-page="reviewPage"
        placeholder="筛选学生、表单、摘要"
        :total="filteredReviewForms.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedReviewForms" style="margin-top: 16px">
          <el-table-column prop="studentName" label="学生" width="120" />
          <el-table-column prop="templateName" label="表单" min-width="150" />
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column label="内容摘要" min-width="220">
            <template #default="{ row }">{{ row.content?.summary }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button link type="primary" @click="openFormReview(row)">审核</el-button></template>
          </el-table-column>
          <template #empty><el-empty description="暂无待审核表单" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'guidance'">
      <div class="page-header">
        <div>
          <h2>指导记录</h2>
          <div class="subtle">登记线上线下指导情况、问题分析和整改建议。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="guidanceDialog = true">新增指导记录</el-button>
      </div>
      <FilterTablePanel
        v-model:keyword="guidanceKeyword"
        v-model:current-page="guidancePage"
        placeholder="筛选学生、问题、建议"
        :total="filteredGuidanceRecords.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedGuidanceRecords" style="margin-top: 16px">
          <el-table-column label="学生" min-width="120"><template #default="{ row }">{{ row.student?.name }}</template></el-table-column>
          <el-table-column prop="guidanceAt" label="指导时间" width="180" />
          <el-table-column prop="mode" label="方式" width="100" />
          <el-table-column prop="problem" label="问题" min-width="160" />
          <el-table-column prop="advice" label="建议" min-width="180" />
          <el-table-column prop="followUp" label="跟进结果" min-width="180" />
          <template #empty><el-empty description="暂无指导记录" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'evaluations'">
      <div class="page-header">
        <div>
          <h2>评价管理</h2>
          <div class="subtle">填写多维评价、优点亮点和改进建议，并提交学院确认最终成绩。</div>
        </div>
        <el-button type="primary" color="#0f766e" :disabled="!activeStudents.length" @click="openEvaluationDialog(activeStudents[0])">新增评价</el-button>
      </div>
      <div class="panel-card">
        <el-table :data="evaluations">
          <el-table-column label="学生" width="120"><template #default="{ row }">{{ row.student?.name }}</template></el-table-column>
          <el-table-column label="评价维度" min-width="260">
            <template #default="{ row }">
              <el-space wrap>
                <el-tag v-for="item in row.dimensionScores || []" :key="`${row.id}-${item.key}`" type="success">{{ item.label }} {{ item.score }}</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column prop="strengthsComment" label="优点亮点" min-width="180" />
          <el-table-column prop="improvementComment" label="改进建议" min-width="180" />
          <el-table-column prop="recommendedScore" label="建议成绩" width="100" />
          <el-table-column label="学院状态" width="100">
            <template #default="{ row }">{{ row.confirmedByCollege ? "已确认" : "待确认" }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button link type="primary" @click="openEvaluationDialog(row)">编辑</el-button></template>
          </el-table-column>
          <template #empty><el-empty description="暂无评价记录" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'alerts'">
      <div class="page-header">
        <div>
          <h2>预警催办</h2>
          <div class="subtle">查看超时审核、退回未改和待学院确认的风险任务，并一键发送催办提醒。</div>
        </div>
      </div>
      <FilterTablePanel
        v-model:keyword="alertKeyword"
        v-model:current-page="alertPage"
        placeholder="筛选类型、标题、对象、级别"
        :total="filteredAlerts.length"
        :page-size="pageSize"
      >
        <el-table :data="pagedAlerts" style="margin-top: 16px">
          <el-table-column prop="category" label="类型" width="120" />
          <el-table-column label="级别" width="100">
            <template #default="{ row }"><el-tag :type="row.level === 'danger' ? 'danger' : 'warning'">{{ row.level === "danger" ? "高" : "中" }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="title" label="预警标题" min-width="220" />
          <el-table-column prop="content" label="说明" min-width="260" />
          <el-table-column prop="targetName" label="催办对象" width="120" />
          <el-table-column prop="overdueDays" label="超时天数" width="100" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button v-if="row.remindable" link type="primary" @click="sendReminder(row)">{{ row.remindActionLabel || "发送催办" }}</el-button>
              <span v-else class="subtle">请尽快处理</span>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无风险预警" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <template v-else-if="section === 'messages'">
      <div class="page-header">
        <div>
          <h2>消息中心</h2>
          <div class="subtle">聚合审核待办、学生提醒和系统结果反馈。</div>
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
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="content" label="内容" min-width="240" />
          <el-table-column label="状态" width="100"><template #default="{ row }">{{ row.read ? "已读" : "未读" }}</template></el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button v-if="!row.read" link type="primary" @click="markRead(row)">标记已读</el-button><span v-else class="subtle">已处理</span></template>
          </el-table-column>
          <template #empty><el-empty description="暂无消息" /></template>
        </el-table>
      </FilterTablePanel>
    </template>

    <el-dialog v-model="mentorReviewDialog" title="处理指导申请" width="520px">
      <el-form label-position="top">
        <el-form-item label="审核结论"><el-switch v-model="mentorReviewForm.approved" inline-prompt active-text="同意" inactive-text="驳回" /></el-form-item>
        <el-form-item label="审核意见"><el-input v-model="mentorReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="mentorReviewDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitMentorReview">提交处理</el-button></template>
    </el-dialog>

    <el-dialog v-model="formReviewDialog" title="审核表单" width="560px">
      <el-form label-position="top">
        <el-form-item label="审核结论"><el-switch v-model="formReviewForm.approved" inline-prompt active-text="通过" inactive-text="退回" /></el-form-item>
        <el-form-item label="评分"><el-input-number v-model="formReviewForm.score" :min="0" :max="100" style="width: 100%" /></el-form-item>
        <el-form-item label="审核意见"><el-input v-model="formReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formReviewDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitFormReview">提交审核</el-button></template>
    </el-dialog>

    <el-dialog v-model="guidanceDialog" title="新增指导记录" width="640px">
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
                <el-option label="线上" value="线上" />
                <el-option label="线下" value="线下" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="学生问题"><el-input v-model="guidanceForm.problem" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="整改建议"><el-input v-model="guidanceForm.advice" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="跟进结果"><el-input v-model="guidanceForm.followUp" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="guidanceDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitGuidanceRecord">保存记录</el-button></template>
    </el-dialog>

    <el-dialog v-model="evaluationDialog" title="填写评价" width="640px">
      <el-form label-position="top">
        <el-form-item label="学生">
          <el-select v-model="evaluationForm.studentId" placeholder="请选择学生" style="width: 100%">
            <el-option v-for="item in activeStudents" :key="item.id" :label="`${item.name} / ${item.studentNo}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <div v-for="item in evaluationForm.dimensionScores" :key="item.key" class="panel-card" style="margin-bottom: 12px">
          <div class="page-header">
            <h2 style="font-size: 16px">{{ item.label }}</h2>
            <el-input-number v-model="item.score" :min="0" :max="100" />
          </div>
          <el-input v-model="item.comment" type="textarea" :rows="2" :placeholder="`请输入${item.label}评价说明`" />
        </div>
        <el-form-item label="阶段评价"><el-input v-model="evaluationForm.stageComment" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="总结评价"><el-input v-model="evaluationForm.summaryComment" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="优点亮点"><el-input v-model="evaluationForm.strengthsComment" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="改进建议"><el-input v-model="evaluationForm.improvementComment" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="建议成绩"><el-input-number v-model="evaluationForm.finalScore" :min="0" :max="100" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="evaluationDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitEvaluation">提交评价</el-button></template>
    </el-dialog>
  </div>
</template>
