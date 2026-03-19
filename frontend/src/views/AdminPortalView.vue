<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { get, post } from "../api/http";

const route = useRoute();
const loading = ref(false);
const dashboard = ref({});
const collegeApplications = ref([]);
const basicData = ref({});
const logs = ref([]);
const section = computed(() => route.meta.section || "dashboard");

const reviewDialog = ref(false);
const currentRow = ref(null);
const reviewForm = reactive({ approved: true, comment: "" });

const applicationKeyword = ref("");
const logKeyword = ref("");
const applicationPage = ref(1);
const logPage = ref(1);
const pageSize = 6;

function containsKeyword(values, keyword) {
  return values.filter(Boolean).some((value) => String(value).includes(keyword));
}

const filteredCollegeApplications = computed(() => {
  const keyword = applicationKeyword.value.trim();
  return keyword ? collegeApplications.value.filter((item) => containsKeyword([item.schoolName, item.collegeName, item.contactName, item.contactPhone, item.status], keyword)) : collegeApplications.value;
});
const filteredLogs = computed(() => {
  const keyword = logKeyword.value.trim();
  return keyword ? logs.value.filter((item) => containsKeyword([item.type, item.action, item.detail, item.operatorId], keyword)) : logs.value;
});

const pagedCollegeApplications = computed(() =>
  filteredCollegeApplications.value.slice((applicationPage.value - 1) * pageSize, applicationPage.value * pageSize)
);
const pagedLogs = computed(() => filteredLogs.value.slice((logPage.value - 1) * pageSize, logPage.value * pageSize));

async function loadAll() {
  loading.value = true;
  try {
    const [dashboardData, applicationData, basicDataResult, logData] = await Promise.all([
      get("/dashboard"),
      get("/admin/college-applications"),
      get("/admin/basic-data"),
      get("/admin/logs"),
    ]);
    dashboard.value = dashboardData;
    collegeApplications.value = applicationData;
    basicData.value = basicDataResult;
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
  reviewDialog.value = true;
}

async function submitReview() {
  try {
    await post(`/admin/college-applications/${currentRow.value.id}/review`, reviewForm);
    ElMessage.success("入驻申请已处理。");
    reviewDialog.value = false;
    await loadAll();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

watch([applicationKeyword, logKeyword], () => {
  applicationPage.value = 1;
  logPage.value = 1;
});

onMounted(loadAll);
watch(() => route.path, loadAll);
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
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="applicationKeyword" placeholder="筛选学校、学院、联系人、状态" clearable style="max-width: 340px" /></div>
        <el-table :data="pagedCollegeApplications">
          <el-table-column prop="schoolName" label="学校" min-width="180" />
          <el-table-column prop="collegeName" label="学院" min-width="180" />
          <el-table-column prop="contactName" label="联系人" width="110" />
          <el-table-column prop="contactPhone" label="联系电话" width="140" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openReview(row)">审核</el-button></template></el-table-column>
          <template #empty><el-empty description="暂无学院入驻申请" /></template>
        </el-table>
        <el-pagination v-if="filteredCollegeApplications.length > pageSize" v-model:current-page="applicationPage" layout="prev, pager, next" :page-size="pageSize" :total="filteredCollegeApplications.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
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
    </template>

    <template v-else-if="section === 'logs'">
      <div class="page-header">
        <div>
          <h2>日志审计</h2>
          <div class="subtle">查看近期登录日志和关键业务动作留痕，帮助平台侧排查操作责任链路。</div>
        </div>
      </div>
      <div class="panel-card">
        <div class="toolbar"><el-input v-model="logKeyword" placeholder="筛选类型、动作、详情、操作人" clearable style="max-width: 340px" /></div>
        <el-table :data="pagedLogs">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="action" label="动作" min-width="180" />
          <el-table-column prop="detail" label="详情" min-width="320" />
          <el-table-column prop="operatorId" label="操作人" width="150" />
          <el-table-column prop="createdAt" label="时间" width="180" />
          <template #empty><el-empty description="暂无日志记录" /></template>
        </el-table>
        <el-pagination v-if="filteredLogs.length > pageSize" v-model:current-page="logPage" layout="prev, pager, next" :page-size="pageSize" :total="filteredLogs.length" style="margin-top: 16px; justify-content: flex-end" />
      </div>
    </template>

    <el-dialog v-model="reviewDialog" title="审核学院入驻申请" width="520px">
      <el-form label-position="top">
        <el-form-item label="审核结论"><el-switch v-model="reviewForm.approved" inline-prompt active-text="通过" inactive-text="驳回" /></el-form-item>
        <el-form-item label="审核意见"><el-input v-model="reviewForm.comment" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewDialog = false">取消</el-button><el-button type="primary" color="#0f766e" @click="submitReview">提交审核</el-button></template>
    </el-dialog>
  </div>
</template>
