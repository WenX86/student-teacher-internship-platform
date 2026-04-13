import { createRouter, createWebHistory } from "vue-router";

const LoginView = () => import("../views/LoginViewCenter.vue");
const StudentPortalView = () => import("../views/StudentPortalView.vue");
const TeacherPortalView = () => import("../views/TeacherPortalView.vue");
const CollegePortalView = () => import("../views/CollegePortalView.vue");
const AdminPortalView = () => import("../views/AdminPortalView.vue");
const PortalLayout = () => import("../layouts/PortalLayout.vue");

const routes = [
  {
    path: "/",
    redirect: "/login",
  },
  {
    path: "/login",
    name: "login",
    component: LoginView,
    meta: {
      public: true,
    },
  },
  {
    path: "/student",
    component: PortalLayout,
    meta: { role: "STUDENT" },
    children: [
      { path: "", redirect: "/student/dashboard" },
      { path: "dashboard", name: "student-dashboard", component: StudentPortalView, meta: { role: "STUDENT", section: "dashboard" } },
      { path: "mentor-applications", name: "student-mentor", component: StudentPortalView, meta: { role: "STUDENT", section: "mentor" } },
      { path: "internship-application", name: "student-internship", component: StudentPortalView, meta: { role: "STUDENT", section: "internship" } },
      { path: "forms", name: "student-forms", component: StudentPortalView, meta: { role: "STUDENT", section: "forms" } },
      { path: "messages", name: "student-messages", component: StudentPortalView, meta: { role: "STUDENT", section: "messages" } },
      { path: "results", name: "student-results", component: StudentPortalView, meta: { role: "STUDENT", section: "results" } },
    ],
  },
  {
    path: "/teacher",
    component: PortalLayout,
    meta: { role: "TEACHER" },
    children: [
      { path: "", redirect: "/teacher/dashboard" },
      { path: "dashboard", name: "teacher-dashboard", component: TeacherPortalView, meta: { role: "TEACHER", section: "dashboard" } },
      { path: "mentor-requests", name: "teacher-mentor", component: TeacherPortalView, meta: { role: "TEACHER", section: "mentor" } },
      { path: "reviews", name: "teacher-reviews", component: TeacherPortalView, meta: { role: "TEACHER", section: "reviews" } },
      { path: "guidance-records", name: "teacher-guidance", component: TeacherPortalView, meta: { role: "TEACHER", section: "guidance" } },
      { path: "evaluations", name: "teacher-evaluations", component: TeacherPortalView, meta: { role: "TEACHER", section: "evaluations" } },
      { path: "alerts", name: "teacher-alerts", component: TeacherPortalView, meta: { role: "TEACHER", section: "alerts" } },
      { path: "messages", name: "teacher-messages", component: TeacherPortalView, meta: { role: "TEACHER", section: "messages" } },
    ],
  },
  {
    path: "/college",
    component: PortalLayout,
    meta: { role: "COLLEGE_ADMIN" },
    children: [
      { path: "", redirect: "/college/dashboard" },
      { path: "dashboard", name: "college-dashboard", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "dashboard" } },
      { path: "students", name: "college-students", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "students" } },
      { path: "teachers", name: "college-teachers", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "teachers" } },
      { path: "mentor-relations", name: "college-mentor", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "mentor" } },
      { path: "organizations", name: "college-organizations", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "organizations" } },
      { path: "internship-applications", name: "college-internship", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "internship" } },
      { path: "archive", name: "college-archive", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "archive" } },
      { path: "evaluations", name: "college-evaluations", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "evaluations" } },
      { path: "alerts", name: "college-alerts", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "alerts" } },
      { path: "reports", name: "college-reports", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "reports" } },
      { path: "messages", name: "college-messages", component: CollegePortalView, meta: { role: "COLLEGE_ADMIN", section: "messages" } },
    ],
  },
  {
    path: "/admin",
    component: PortalLayout,
    meta: { role: "SUPER_ADMIN" },
    children: [
      { path: "", redirect: "/admin/dashboard" },
      { path: "dashboard", name: "admin-dashboard", component: AdminPortalView, meta: { role: "SUPER_ADMIN", section: "dashboard" } },
      { path: "basic-data", name: "admin-basic", component: AdminPortalView, meta: { role: "SUPER_ADMIN", section: "basic" } },
      { path: "params", name: "admin-params", component: AdminPortalView, meta: { role: "SUPER_ADMIN", section: "params" } },
      { path: "form-templates", name: "admin-templates", component: AdminPortalView, meta: { role: "SUPER_ADMIN", section: "templates" } },
      { path: "logs", name: "admin-logs", component: AdminPortalView, meta: { role: "SUPER_ADMIN", section: "logs" } },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  if (to.meta.public) {
    return true;
  }

  const token = localStorage.getItem("internship-token");
  const user = JSON.parse(localStorage.getItem("internship-user") || "null");

  if (!token || !user) {
    return "/login";
  }

  if (to.meta.role && user.role !== to.meta.role) {
    if (user.role === "STUDENT") return "/student/dashboard";
    if (user.role === "TEACHER") return "/teacher/dashboard";
    if (user.role === "COLLEGE_ADMIN") return "/college/dashboard";
    if (user.role === "SUPER_ADMIN") return "/admin/dashboard";
  }

  return true;
});

export default router;
