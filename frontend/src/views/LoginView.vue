<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus/es/components/message/index";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);

const form = reactive({
  account: "college01",
  password: "123456",
});

const quickAccounts = [
  { label: "学生", account: "20230001", password: "123456" },
  { label: "教师", account: "T1001", password: "123456" },
  { label: "学院管理员", account: "college01", password: "123456" },
  { label: "超级管理员", account: "root", password: "123456" },
];

function resolveHome(role) {
  if (role === "STUDENT") return "/student/dashboard";
  if (role === "TEACHER") return "/teacher/dashboard";
  if (role === "COLLEGE_ADMIN") return "/college/dashboard";
  return "/admin/dashboard";
}

function useQuickAccount(item) {
  form.account = item.account;
  form.password = item.password;
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
    ElMessage.success(`欢迎回来，${user.name}`);
    router.push(resolveHome(user.role));
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div
    style="min-height:100vh;display:grid;grid-template-columns:1.08fr 0.92fr;background:linear-gradient(135deg,#0f766e 0%,#1d4ed8 55%,#ea580c 100%)"
  >
    <section style="padding:56px;color:white;display:flex;flex-direction:column;justify-content:center">
      <div style="font-size:14px;letter-spacing:2px;opacity:.78">PHASE 01</div>
      <h1 style="font-size:46px;line-height:1.15;max-width:720px;margin:18px 0 14px">
        师范生教育实习全过程管理平台
      </h1>
      <p style="max-width:640px;font-size:18px;line-height:1.8;opacity:.88">
        围绕指导关系建立、实习申请审批、核心表单审核归档三条主链，支持学生、教师、学院管理员、超级管理员协同完成一期业务。
      </p>
      <el-row :gutter="16" style="margin-top: 28px; max-width: 760px">
        <el-col :span="12" v-for="item in quickAccounts" :key="item.label">
          <div
            style="padding:18px 20px;margin-bottom:14px;border-radius:20px;background:rgba(255,255,255,.12);backdrop-filter:blur(10px)"
          >
            <div style="font-size:18px;font-weight:700">{{ item.label }}</div>
            <div style="font-size:13px;margin-top:8px;opacity:.82">{{ item.account }} / {{ item.password }}</div>
            <el-button style="margin-top:14px" color="#ffffff" plain @click="useQuickAccount(item)">填入账号</el-button>
          </div>
        </el-col>
      </el-row>
    </section>

    <section style="display:flex;align-items:center;justify-content:center;padding:32px">
      <div
        style="width:min(460px,100%);padding:34px;border-radius:28px;background:rgba(255,255,255,.92);box-shadow:0 30px 80px rgba(15,23,42,.25)"
      >
        <div style="font-size:28px;font-weight:700;color:#0f172a">登录平台</div>
        <div class="subtle" style="margin:10px 0 24px">演示账号已预置，可直接进入联调流程。</div>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="form.account" placeholder="请输入学号、工号或管理员账号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" color="#0f766e" size="large" style="width:100%" :loading="loading" @click="submit">
            进入一期工作台
          </el-button>
        </el-form>
      </div>
    </section>
  </div>
</template>