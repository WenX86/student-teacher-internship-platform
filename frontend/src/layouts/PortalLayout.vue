<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { useAuthStore } from "../stores/auth";
import { useMessageStore } from "../stores/message";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const messageStore = useMessageStore();
const changePasswordDialogVisible = ref(false);
const savingPassword = ref(false);
const passwordForm = reactive({
  newPassword: "",
  confirmPassword: "",
});

const menus = {
  STUDENT: [
    { label: "工作台", path: "/student/dashboard" },
    { label: "指导教师申请", path: "/student/mentor-applications" },
    { label: "实习申请", path: "/student/internship-application" },
    { label: "核心表单", path: "/student/forms" },
    { label: "消息中心", path: "/student/messages" },
    { label: "结果查看", path: "/student/results" },
  ],
  TEACHER: [
    { label: "工作台", path: "/teacher/dashboard" },
    { label: "指导申请确认", path: "/teacher/mentor-requests" },
    { label: "材料审核", path: "/teacher/reviews" },
    { label: "指导记录", path: "/teacher/guidance-records" },
    { label: "评价管理", path: "/teacher/evaluations" },
    { label: "预警催办", path: "/teacher/alerts" },
    { label: "消息中心", path: "/teacher/messages" },
  ],
  COLLEGE_ADMIN: [
    { label: "学院看板", path: "/college/dashboard" },
    { label: "学生管理", path: "/college/students" },
    { label: "教师管理", path: "/college/teachers" },
    { label: "指导复核", path: "/college/mentor-relations" },
    { label: "实习单位", path: "/college/organizations" },
    { label: "实习审批", path: "/college/internship-applications" },
    { label: "归档中心", path: "/college/archive" },
    { label: "评价汇总", path: "/college/evaluations" },
    { label: "预警催办", path: "/college/alerts" },
    { label: "统计报表", path: "/college/reports" },
    { label: "消息中心", path: "/college/messages" },
  ],
  SUPER_ADMIN: [
    { label: "平台看板", path: "/admin/dashboard" },
    { label: "学院申请", path: "/admin/basic-data" },
    { label: "参数配置", path: "/admin/params" },
    { label: "表单模板", path: "/admin/form-templates" },
    { label: "日志审计", path: "/admin/logs" },
  ],
};

const roleLabelMap = {
  STUDENT: "学生端",
  TEACHER: "指导教师端",
  COLLEGE_ADMIN: "学院管理员端",
  SUPER_ADMIN: "超级管理员端",
};

const currentMenus = computed(() => menus[authStore.user?.role] || []);
const roleLabel = computed(() => roleLabelMap[authStore.user?.role] || "平台用户");
const messageBadgeValue = computed(() => (messageStore.unreadCount > 99 ? "99+" : messageStore.unreadCount));

function isMessageMenuItem(item) {
  return typeof item?.path === "string" && item.path.endsWith("/messages");
}

function resetPasswordForm() {
  passwordForm.newPassword = "";
  passwordForm.confirmPassword = "";
}

function openChangePasswordDialog() {
  resetPasswordForm();
  changePasswordDialogVisible.value = true;
}

function handleLogout() {
  authStore.logout();
  messageStore.reset();
  router.push("/login");
}

function handlePasswordDialogClose(done) {
  if (authStore.user?.mustChangePassword) {
    return;
  }
  resetPasswordForm();
  done();
}

async function syncCurrentUser() {
  if (!authStore.token) {
    return;
  }

  try {
    await authStore.refreshUser();
  } catch (error) {
    authStore.logout();
    messageStore.reset();
    router.push("/login");
    ElMessage.error(error.message);
  }
}

watch(
  () => authStore.token,
  async (token) => {
    if (!token) {
      messageStore.reset();
      return;
    }

    await messageStore.refreshUnreadCount();
  },
  { immediate: true },
);

async function submitPasswordChange() {
  const nextPassword = passwordForm.newPassword.trim();
  const confirmPassword = passwordForm.confirmPassword.trim();
  const forcedChange = authStore.user?.mustChangePassword === true;

  if (!nextPassword) {
    ElMessage.warning("请输入新密码。");
    return;
  }

  if (nextPassword.length < 6) {
    ElMessage.warning("新密码长度不能少于 6 位。");
    return;
  }

  if (!confirmPassword) {
    ElMessage.warning("请再次输入新密码。");
    return;
  }

  if (nextPassword !== confirmPassword) {
    ElMessage.warning("两次输入的新密码不一致。");
    return;
  }

  savingPassword.value = true;
  try {
    await authStore.changePassword(nextPassword);
    changePasswordDialogVisible.value = false;
    resetPasswordForm();
    ElMessage.success(forcedChange ? "密码修改成功，请继续使用平台。" : "密码修改成功。");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    savingPassword.value = false;
  }
}

watch(
  () => authStore.user?.mustChangePassword,
  (mustChangePassword) => {
    if (mustChangePassword) {
      openChangePasswordDialog();
    } else if (!savingPassword.value) {
      changePasswordDialogVisible.value = false;
    }
  },
  { immediate: true },
);

onMounted(syncCurrentUser);
</script>

<template>
  <el-container style="min-height: 100vh">
    <el-aside width="240px" style="background: linear-gradient(180deg, #134e4a 0%, #0f172a 100%); color: white">
      <div style="padding: 28px 22px 18px; border-bottom: 1px solid rgba(255,255,255,0.1)">
        <div style="font-size: 12px; opacity: 0.8">一期建设</div>
        <div style="font-size: 20px; font-weight: 700; margin-top: 6px">师范生教育实习全过程管理平台</div>
        <div style="font-size: 13px; margin-top: 8px; opacity: 0.75">{{ roleLabel }}</div>
      </div>
      <el-menu
        :default-active="route.path"
        background-color="transparent"
        text-color="#d1fae5"
        active-text-color="#fff7ed"
        style="border-right: none"
        router
      >
        <el-menu-item v-for="item in currentMenus" :key="item.path" :index="item.path">
          <span class="menu-item-row">
            <span class="menu-item-label">{{ item.label }}</span>
            <span v-if="isMessageMenuItem(item) && messageStore.unreadCount > 0" class="menu-item-badge">
              {{ messageBadgeValue }}
            </span>
          </span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header
        style="display:flex;align-items:center;justify-content:space-between;padding:18px 28px;background:rgba(255,255,255,0.7);backdrop-filter: blur(12px);border-bottom:1px solid rgba(15,23,42,0.08)"
      >
        <div>
          <div style="font-size: 22px; font-weight: 700">{{ authStore.user?.name }}</div>
          <div class="subtle">{{ authStore.user?.account }} | {{ roleLabel }}</div>
        </div>
        <div style="display:flex;align-items:center;gap:12px">
          <el-tag v-if="authStore.user?.mustChangePassword" type="danger">首次登录待改密</el-tag>
          <el-badge v-if="messageStore.unreadCount > 0" :value="messageBadgeValue" :max="99" type="danger">
            <el-tag type="warning">消息提醒</el-tag>
          </el-badge>
          <el-tag type="success">一期主链已打通</el-tag>
          <el-button plain @click="openChangePasswordDialog">修改密码</el-button>
          <el-button plain @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main style="padding: 28px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog
    v-model="changePasswordDialogVisible"
    :title="authStore.user?.mustChangePassword ? '首次登录请修改密码' : '修改密码'"
    width="460px"
    :show-close="!authStore.user?.mustChangePassword"
    :close-on-click-modal="!authStore.user?.mustChangePassword"
    :close-on-press-escape="!authStore.user?.mustChangePassword"
    :before-close="handlePasswordDialogClose"
  >
    <div class="subtle" style="margin-bottom: 18px">
      {{ authStore.user?.mustChangePassword ? "当前账号仍在使用初始密码，继续操作前请先完成修改。" : "建议定期更新密码，保障账号安全。" }}
    </div>
    <el-form label-position="top" @submit.prevent="submitPasswordChange">
      <el-form-item label="新密码">
        <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入不少于 6 位的新密码" />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="!authStore.user?.mustChangePassword" @click="changePasswordDialogVisible = false">取消</el-button>
      <el-button type="primary" color="#0f766e" :loading="savingPassword" @click="submitPasswordChange">确认修改</el-button>
    </template>
  </el-dialog>
</template>
