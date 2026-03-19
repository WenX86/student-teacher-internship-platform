<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { get, post } from "../api/http";

const route = useRoute();
const loading = ref(false);
const dashboard = ref({});
const mentorApplications = ref([]);
const forms = ref([]);
const guidanceRecords = ref([]);
const evaluations = ref([]);
const messages = ref([]);

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
});

const reviewKeyword = ref("");
const guidanceKeyword = ref("");
const messageKeyword = ref("");
const reviewPage = ref(1);
const guidancePage = ref(1);
const messagePage = ref(1);
const pageSize = 5;

const section = computed(() => route.meta.section || "dashboard");
const activeStudents = computed(() =>
  mentorApplications.value.filter((item) => item.status === "已生效").map((item) => item.student)
);
const pendingMentorApplications = computed(() =>
  mentorApplications.value.filter((item) => item.status === "待教师确认")
);

const filteredReviewForms = computed(() => {
  const keyword = reviewKeyword.value.trim();
  const base = forms.value.filter((item) => item.status === "教师审核中");
  if (!keyword) return base;
  return base.filter((item) =>
    [item.studentName, item.templateName, item.content?.summary].filter(Boolean).some((value) => String(value).includes(keyword))
  );
});

const filteredGuidanceRecords = computed(() => {
  const keyword = guidanceKeyword.value.trim();
  if (!keyword) return guidanceRecords.value;
  return guidanceRecords.value.filter((item) =>
    [item.student?.name, item.problem, item.advice, item.followUp].filter(Boolean).some((value) => String(value).includes(keyword))
  );
});

const filteredMessages = computed(() => {
  const keyword = messageKeyword.value.trim();
  if (!keyword) return messages.value;
  return messages.value.filter((item) =>
    [item.type, item.title, item.content].filter(Boolean).some((value) => String(value).includes(keyword))
  );
});

const pagedReviewForms = computed(() => filteredReviewForms.value.slice((reviewPage.value - 1) * pageSize, reviewPage.value * pageSize));
const pagedGuidanceRecords = computed(() =>
  filteredGuidanceRecords.value.slice((guidancePage.value - 1) * pageSize, guidancePage.value * pageSize)
);
const pagedMessages = computed(() => filteredMessages.value.slice((messagePage.value - 1) * pageSize, messagePage.value * pageSize));

async function loadAll() {
  loading.value = true;
  try {
    const [dashboardData, mentorData, formData, guidanceData, evaluationData, messageData] = await Promise.all([
      get("/dashboard"),
      get("/mentor-applications"),
      get("/forms"),
      get("/guidance-records"),
      get("/evaluations"),
      get("/messages"),
    ]);
    dashboard.value = dashboardData;
    mentorApplications.value = mentorData;
    forms.value = formData;
    guidanceRecords.value = guidanceData;
    evaluations.value = evaluationData;
    messages.value = messageData;
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
    ElMessage.success("指导申请处理完成");
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
    ElMessage.success("表单审核完成");
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
    ElMessage.success("指导记录已保存");
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

function openEvaluationDialog(row) {
  evaluationForm.studentId = row?.student?.id || row?.id || "";
  evaluationForm.stageComment = row?.stageComment || "";
  evaluationForm.summaryComment = row?.summaryComment || "";
  evaluationForm.finalScore = row?.finalScore || 90;
  evaluationDialog.value = true;
}

async function submitEvaluation() {
  if (!evaluationForm.studentId) {
    ElMessage.warning("请选择学生。");
    return;
  }
  try {
    await post("/evaluations", evaluationForm);
    ElMessage.success("评价已提交学院汇总");
    evaluationDialog.value = false;
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

watch([reviewKeyword, guidanceKeyword, messageKeyword], () => {
  reviewPage.value = 1;
  guidancePage.value = 1;
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
          <h2>教师工作台</h2>
          <div class="subtle">聚合待确认指导申请、待审核材料与学生整体完成率。</div>
        </div>
      </div>
      <div class="metric-grid">
        <div class="metric-card"><h4>负责学生数</h4><strong>{{ dashboard.studentCount || 0 }}</strong></div>
        <div class="metric-card"><h4>待确认指导申请</h4><strong>{{ dashboard.pendingMentorRequests || 0 }}</strong></div>
        <div class="metric-card"><h4>待审核材料</h4><strong>{{ dashboard.pendingReviewCount || 0 }}</strong></div>
        <div class="metric-card"><h4>材料完成率</h4><strong>{{ dashboard.completionRate || 0 }}%</strong></div>
      </div>
      <div class="panel-card">
        <div class="page-header"><h2 style="font-size:20px">已生效学生名单</h2></div>
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
          <div class="subtle">教师仅确认或驳回申请，最终生效仍由学院管理员复核。</div>
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
          <div class="subtle">一期表单统一进入教师审核链路，再流转到学院归档。</div>
        </div>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="reviewKeyword" placeholder="筛选学生、表单、摘要" clearable style="max-width: 320px" /></div>
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
        <el-pagination
          v-if="filteredReviewForms.length > pageSize"
          v-model:current-page="reviewPage"
          layout="prev, pager, next"
          :page-size="pageSize"
          :total="filteredReviewForms.length"
          style="margin-top: 16px; justify-content: flex-end"
        />
      </div>
    </template>

    <template v-else-if="section === 'guidance'">
      <div class="page-header">
        <div>
          <h2>指导记录</h2>
          <div class="subtle">登记线上线下指导、问题分析与整改建议。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="guidanceDialog = true">新增指导记录</el-button>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="guidanceKeyword" placeholder="筛选学生、问题、建议" clearable style="max-width: 320px" /></div>
        <el-table :data="pagedGuidanceRecords" style="margin-top: 16px">
          <el-table-column label="学生" min-width="120"><template #default="{ row }">{{ row.student?.name }}</template></el-table-column>
          <el-table-column prop="guidanceAt" label="指导时间" width="180" />
          <el-table-column prop="mode" label="方式" width="100" />
          <el-table-column prop="problem" label="问题" min-width="160" />
          <el-table-column prop="advice" label="建议" min-width="180" />
          <el-table-column prop="followUp" label="跟进结果" min-width="180" />
          <template #empty><el-empty description="暂无指导记录" /></template>
        </el-table>
        <el-pagination
          v-if="filteredGuidanceRecords.length > pageSize"
          v-model:current-page="guidancePage"
          layout="prev, pager, next"
          :page-size="pageSize"
          :total="filteredGuidanceRecords.length"
          style="margin-top: 16px; justify-content: flex-end"
        />
      </div>
    </template>

    <template v-else-if="section === 'evaluations'">
      <div class="page-header">
        <div>
          <h2>评价管理</h2>
          <div class="subtle">填写阶段评价与总结评价，提交学院汇总确认最终成绩。</div>
        </div>
        <el-button type="primary" color="#0f766e" @click="openEvaluationDialog(activeStudents[0])">新增评价</el-button>
      </div>
      <div class="panel-card">
        <el-table :data="evaluations">
          <el-table-column label="学生" width="120"><template #default="{ row }">{{ row.student?.name }}</template></el-table-column>
          <el-table-column prop="stageComment" label="阶段评价" min-width="180" />
          <el-table-column prop="summaryComment" label="总结评价" min-width="220" />
          <el-table-column prop="finalScore" label="成绩" width="100" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button link type="primary" @click="openEvaluationDialog(row)">编辑</el-button></template>
          </el-table-column>
          <template #empty><el-empty description="暂无评价记录" /></template>
        </el-table>
      </div>
    </template>

    <template v-else-if="section === 'messages'">
      <div class="page-header">
        <div>
          <h2>消息中心</h2>
          <div class="subtle">聚合审核待办、学生提醒与系统结果反馈。</div>
        </div>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="messageKeyword" placeholder="筛选类型、标题、内容" clearable style="max-width: 320px" /></div>
        <el-table :data="pagedMessages" style="margin-top: 16px">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="content" label="内容" min-width="240" />
          <el-table-column label="状态" width="100"><template #default="{ row }">{{ row.read ? "已读" : "未读" }}</template></el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button link type="primary" @click="markRead(row)">标记已读</el-button></template>
          </el-table-column>
          <template #empty><el-empty description="暂无消息" /></template>
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
        <el-form-item label="评分"><el-input-number v-model="formReviewForm.score" :min="0" :max="100" /></el-form-item>
        <el-form-item label="审核意见"><el-input v-model="formReviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formReviewDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitFormReview">提交审核</el-button></template>
    </el-dialog>

    <el-dialog v-model="guidanceDialog" title="新增指导记录" width="640px">
      <el-form label-position="top">
        <el-form-item label="学生">
          <el-select v-model="guidanceForm.studentId" placeholder="请选择学生" style="width:100%">
            <el-option v-for="item in activeStudents" :key="item.id" :label="`${item.name} / ${item.studentNo}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="指导时间"><el-input v-model="guidanceForm.guidanceAt" placeholder="YYYY-MM-DD HH:mm" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="指导方式">
              <el-select v-model="guidanceForm.mode" style="width:100%">
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
          <el-select v-model="evaluationForm.studentId" placeholder="请选择学生" style="width:100%">
            <el-option v-for="item in activeStudents" :key="item.id" :label="`${item.name} / ${item.studentNo}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段评价"><el-input v-model="evaluationForm.stageComment" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="总结评价"><el-input v-model="evaluationForm.summaryComment" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="建议成绩"><el-input-number v-model="evaluationForm.finalScore" :min="0" :max="100" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="evaluationDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitEvaluation">提交评价</el-button></template>
    </el-dialog>
  </div>
</template>
