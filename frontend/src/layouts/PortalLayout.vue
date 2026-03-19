<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

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
    { label: "指导申请待确认", path: "/teacher/mentor-requests" },
    { label: "材料审核", path: "/teacher/reviews" },
    { label: "指导记录", path: "/teacher/guidance-records" },
    { label: "评价管理", path: "/teacher/evaluations" },
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
    { label: "统计报表", path: "/college/reports" },
    { label: "消息中心", path: "/college/messages" },
  ],
  SUPER_ADMIN: [
    { label: "入驻审核", path: "/admin/dashboard" },
    { label: "基础数据", path: "/admin/basic-data" },
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
const roleLabel = computed(() => roleLabelMap[authStore.user?.role] || "平台");

function handleLogout() {
  authStore.logout();
  router.push("/login");
}
</script>

<template>
  <el-container style="min-height: 100vh">
    <el-aside width="240px" style="background: linear-gradient(180deg, #134e4a 0%, #0f172a 100%); color: white">
      <div style="padding: 28px 22px 18px; border-bottom: 1px solid rgba(255,255,255,0.1)">
        <div style="font-size: 12px; opacity: 0.8">一期建设</div>
        <div style="font-size: 20px; font-weight: 700; margin-top: 6px">师范生教育实习平台</div>
        <div style="font-size: 13px; margin-top: 8px; opacity: 0.75">{{ roleLabel }}</div>
      </div>
      <el-menu :default-active="route.path" background-color="transparent" text-color="#d1fae5" active-text-color="#fff7ed" style="border-right: none" router>
        <el-menu-item v-for="item in currentMenus" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display:flex;align-items:center;justify-content:space-between;padding:18px 28px;background:rgba(255,255,255,0.7);backdrop-filter: blur(12px);border-bottom:1px solid rgba(15,23,42,0.08)">
        <div>
          <div style="font-size: 22px; font-weight: 700">{{ authStore.user?.name }}</div>
          <div class="subtle">{{ authStore.user?.account }} | {{ roleLabel }}</div>
        </div>
        <div style="display:flex;align-items:center;gap:12px">
          <el-tag type="success">一期主流程已打通</el-tag>
          <el-button plain @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main style="padding: 28px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
