<script setup>
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const selectedRole = ref("STUDENT");

const form = reactive({
  account: "20230001",
  password: "123456",
});

const roles = [
  { key: "STUDENT", label: "学生", workbench: "学生工作台", accent: "#0f766e", account: "20230001" },
  { key: "TEACHER", label: "指导教师", workbench: "教师工作台", accent: "#2563eb", account: "T1001" },
  { key: "COLLEGE_ADMIN", label: "学院管理员", workbench: "学院工作台", accent: "#7c3aed", account: "college01" },
  { key: "SUPER_ADMIN", label: "超级管理员", workbench: "系统管理台", accent: "#ea580c", account: "root" },
];

const selectedRoleMeta = computed(() => roles.find((item) => item.key === selectedRole.value) || roles[0]);

function selectRole(roleKey) {
  const role = roles.find((item) => item.key === roleKey) || roles[0];
  selectedRole.value = role.key;
  form.account = role.account;
  form.password = "123456";
}

function resolveHome(role) {
  if (role === "STUDENT") return "/student/dashboard";
  if (role === "TEACHER") return "/teacher/dashboard";
  if (role === "COLLEGE_ADMIN") return "/college/dashboard";
  return "/admin/dashboard";
}

async function submit() {
  if (!form.account.trim()) {
    ElMessage.warning("请输入账号。");
    return;
  }

  if (!form.password.trim()) {
    ElMessage.warning("请输入密码。");
    return;
  }

  loading.value = true;
  try {
    const user = await authStore.login(form);
    ElMessage.success(user.mustChangePassword ? `欢迎回来，${user.name}，请先修改初始密码。` : `欢迎回来，${user.name}`);
    router.push(resolveHome(user.role));
  } catch (error) {
    ElMessage.error(error.message || "登录失败，请稍后重试。");
  } finally {
    loading.value = false;
  }
}

selectRole(selectedRole.value);
</script>

<template>
  <div class="login-page" :style="{ '--role-accent': selectedRoleMeta.accent }">
    <div class="login-glow login-glow--one"></div>
    <div class="login-glow login-glow--two"></div>
    <div class="login-glow login-glow--three"></div>

    <div class="login-card">
      <div class="login-card__header">
        <div class="login-card__eyebrow">师范生教育实习全过程管理平台</div>
        <h1 class="login-card__title">统一登录入口</h1>
      </div>

      <div class="role-tabs" role="tablist" aria-label="角色切换">
        <button
          v-for="role in roles"
          :key="role.key"
          type="button"
          class="role-tab"
          :class="{ 'is-active': selectedRole === role.key }"
          @click="selectRole(role.key)"
        >
          {{ role.label }}
        </button>
      </div>

      <div class="role-badge">当前角色：{{ selectedRoleMeta.label }} · {{ selectedRoleMeta.workbench }}</div>

      <el-form class="login-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input
            v-model="form.account"
            autocomplete="username"
            placeholder="请输入学号、工号或管理员账号"
            size="large"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
            size="large"
          />
        </el-form-item>
        <el-button class="login-form__submit" type="primary" size="large" native-type="submit" :loading="loading">
          进入{{ selectedRoleMeta.workbench }}
        </el-button>
      </el-form>

      <div class="login-card__footer">首次登录如提示修改密码，请先完成修改后再继续使用。</div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  padding: 24px;
  background:
    radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.16), transparent 20%),
    radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1), transparent 18%),
    radial-gradient(circle at 50% 85%, rgba(255, 255, 255, 0.08), transparent 20%),
    linear-gradient(135deg, #0f172a 0%, #0f766e 34%, #1d4ed8 68%, #ea580c 100%);
}

.login-glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(36px);
  opacity: 0.35;
  pointer-events: none;
}

.login-glow--one {
  width: 260px;
  height: 260px;
  left: -70px;
  top: -70px;
  background: rgba(255, 255, 255, 0.22);
}

.login-glow--two {
  width: 220px;
  height: 220px;
  right: 10%;
  top: 12%;
  background: rgba(59, 130, 246, 0.28);
}

.login-glow--three {
  width: 320px;
  height: 320px;
  right: -110px;
  bottom: -120px;
  background: rgba(251, 146, 60, 0.28);
}

.login-card {
  position: relative;
  z-index: 1;
  width: min(520px, 100%);
  padding: 30px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.4);
  box-shadow: 0 28px 90px rgba(15, 23, 42, 0.26);
  backdrop-filter: blur(20px);
}

.login-card__header {
  text-align: center;
}

.login-card__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 16px 36px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: var(--role-accent);
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 1px;
  line-height: 1.15;
}

.login-card__title {
  margin: 10px 0 6px;
  font-size: 18px;
  line-height: 1.2;
  font-weight: 800;
  color: #0f172a;
}

.role-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 22px;
}

.role-tab {
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: #f8fafc;
  color: #334155;
  border-radius: 999px;
  padding: 10px 8px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    background 0.2s ease,
    color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.role-tab:hover {
  transform: translateY(-1px);
}

.role-tab.is-active {
  color: #fff;
  background: var(--role-accent);
  border-color: transparent;
  box-shadow: 0 12px 26px color-mix(in srgb, var(--role-accent) 28%, transparent);
}

.role-badge {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(29, 78, 216, 0.08));
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  text-align: center;
}

.login-form {
  margin-top: 18px;
}

.login-form :deep(.el-form-item__label) {
  font-size: 14px;
  font-weight: 700;
  color: #334155;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 14px;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.18) inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--role-accent) inset,
    0 0 0 1px color-mix(in srgb, var(--role-accent) 24%, transparent);
}

.login-form__submit {
  width: 100%;
  margin-top: 6px;
  border: none;
  border-radius: 14px;
  font-weight: 700;
  background: var(--role-accent);
  box-shadow: 0 14px 30px color-mix(in srgb, var(--role-accent) 28%, transparent);
}

.login-card__footer {
  margin-top: 16px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
  text-align: center;
}

@media (max-width: 720px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    padding: 22px;
    border-radius: 24px;
  }

  .login-card__title {
    font-size: 24px;
  }

  .role-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 420px) {
  .role-tabs {
    grid-template-columns: 1fr;
  }
}
</style>
