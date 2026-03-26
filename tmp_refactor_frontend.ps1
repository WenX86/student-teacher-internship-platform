$ErrorActionPreference = "Stop"

function Replace-Once {
  param(
    [string]$Content,
    [string]$Pattern,
    [string]$Replacement,
    [string]$Label
  )

  $regex = [regex]::new($Pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)
  if (-not $regex.IsMatch($Content)) {
    throw "Pattern not found: $Label"
  }

  return $regex.Replace($Content, $Replacement, 1)
}

function Update-File {
  param(
    [string]$Path,
    [scriptblock]$Transform
  )

  $content = [System.IO.File]::ReadAllText($Path, [System.Text.UTF8Encoding]::new($false))
  $updated = & $Transform $content
  [System.IO.File]::WriteAllText($Path, $updated, [System.Text.UTF8Encoding]::new($false))
}

Update-File ".\AdminPortalView.tmp.vue" {
  param($content)

  if ($content -notmatch "useFilteredPagination") {
    $content = $content -replace 'import FilterTablePanel from "\.\./components/FilterTablePanel\.vue";', @"
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";
"@
  }

  $content = Replace-Once -Content $content -Pattern 'const applicationKeyword = ref\(""\);\r?\nconst logKeyword = ref\(""\);\r?\nconst applicationPage = ref\(1\);\r?\nconst logPage = ref\(1\);\r?\nconst pageSize = 6;\r?\n\r?\nfunction containsKeyword\(values, keyword\) \{\r?\n  return values\.filter\(Boolean\)\.some\(\(value\) => String\(value\)\.includes\(keyword\)\);\r?\n\}\r?\n\r?\nconst filteredCollegeApplications = computed\(\(\) => \{.*?const pagedLogs = computed\(\(\) => filteredLogs\.value\.slice\(\(logPage\.value - 1\) \* pageSize, logPage\.value \* pageSize\)\);\r?\n' -Replacement @"
const pageSize = 6;
const {
  keyword: applicationKeyword,
  currentPage: applicationPage,
  filteredItems: filteredCollegeApplications,
  pagedItems: pagedCollegeApplications,
} = useFilteredPagination({
  source: collegeApplications,
  matcher: (item) => [item.schoolName, item.collegeName, item.contactName, item.contactPhone, item.status],
  pageSize,
});
const {
  keyword: logKeyword,
  currentPage: logPage,
  filteredItems: filteredLogs,
  pagedItems: pagedLogs,
} = useFilteredPagination({
  source: logs,
  matcher: (item) => [item.type, item.action, item.detail, item.operatorId],
  pageSize,
});
"@ -Label "admin main block"

  $content = Replace-Once -Content $content -Pattern 'watch\(\[applicationKeyword, logKeyword\], \(\) => \{\r?\n  applicationPage\.value = 1;\r?\n  logPage\.value = 1;\r?\n\}\);\r?\n\r?\n' -Replacement "" -Label "admin reset watch"

  return $content
}

Update-File ".\StudentPortalView.tmp.vue" {
  param($content)

  if ($content -notmatch "useFilteredPagination") {
    $content = $content -replace 'import FilterTablePanel from "\.\./components/FilterTablePanel\.vue";', @"
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";
"@
  }

  $content = Replace-Once -Content $content -Pattern 'const formKeyword = ref\(""\);\r?\nconst messageKeyword = ref\(""\);\r?\nconst internshipKeyword = ref\(""\);\r?\nconst formPage = ref\(1\);\r?\nconst internshipPage = ref\(1\);\r?\nconst messagePage = ref\(1\);\r?\nconst pageSize = 5;\r?\n\r?\nconst section = computed\(\(\) => route\.meta\.section \|\| "dashboard"\);\r?\nconst latestForms = computed\(\(\) => forms\.value\.slice\(0, 5\)\);\r?\nconst unreadMessages = computed\(\(\) => messages\.value\.filter\(\(item\) => !item\.read\)\);\r?\n\r?\nconst filteredForms = computed\(\(\) => \{.*?const pagedMessages = computed\(\(\) =>\r?\n  filteredMessages\.value\.slice\(\(messagePage\.value - 1\) \* pageSize, messagePage\.value \* pageSize\)\r?\n\);\r?\n' -Replacement @"
const pageSize = 5;

const section = computed(() => route.meta.section || "dashboard");
const latestForms = computed(() => forms.value.slice(0, 5));
const unreadMessages = computed(() => messages.value.filter((item) => !item.read));
const {
  keyword: formKeyword,
  currentPage: formPage,
  filteredItems: filteredForms,
  pagedItems: pagedForms,
} = useFilteredPagination({
  source: forms,
  matcher: (item) => [item.templateName, item.status, item.content?.title, item.content?.summary],
  pageSize,
});
const {
  keyword: internshipKeyword,
  currentPage: internshipPage,
  filteredItems: filteredInternships,
  pagedItems: pagedInternships,
} = useFilteredPagination({
  source: internshipApplications,
  matcher: (item) => [item.organization?.name, item.position, item.gradeTarget, item.status],
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
"@ -Label "student main block"

  $content = Replace-Once -Content $content -Pattern 'watch\(\[formKeyword, internshipKeyword, messageKeyword\], \(\) => \{\r?\n  formPage\.value = 1;\r?\n  internshipPage\.value = 1;\r?\n  messagePage\.value = 1;\r?\n\}\);\r?\n\r?\n' -Replacement "" -Label "student reset watch"

  return $content
}

Update-File ".\TeacherPortalView.tmp.vue" {
  param($content)

  if ($content -notmatch "useFilteredPagination") {
    $content = $content -replace 'import FilterTablePanel from "\.\./components/FilterTablePanel\.vue";', @"
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";
"@
  }

  $content = Replace-Once -Content $content -Pattern 'const reviewKeyword = ref\(""\);\r?\nconst guidanceKeyword = ref\(""\);\r?\nconst messageKeyword = ref\(""\);\r?\nconst reviewPage = ref\(1\);\r?\nconst guidancePage = ref\(1\);\r?\nconst messagePage = ref\(1\);\r?\nconst pageSize = 5;\r?\n\r?\nconst section = computed\(\(\) => route\.meta\.section \|\| "dashboard"\);\r?\nconst activeStudents = computed\(\(\) =>\r?\n  mentorApplications\.value\.filter\(\(item\) => item\.status === ".*?"\)\.map\(\(item\) => item\.student\)\r?\n\);\r?\nconst pendingMentorApplications = computed\(\(\) =>\r?\n  mentorApplications\.value\.filter\(\(item\) => item\.status === ".*?"\)\r?\n\);\r?\n\r?\nconst filteredReviewForms = computed\(\(\) => \{.*?const pagedMessages = computed\(\(\) => filteredMessages\.value\.slice\(\(messagePage\.value - 1\) \* pageSize, messagePage\.value \* pageSize\)\);\r?\n' -Replacement @"
const pageSize = 5;

const section = computed(() => route.meta.section || "dashboard");
const activeStudents = computed(() =>
  mentorApplications.value.filter((item) => item.status === "已生效").map((item) => item.student)
);
const pendingMentorApplications = computed(() =>
  mentorApplications.value.filter((item) => item.status === "待教师确认")
);
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
"@ -Label "teacher main block"

  $content = Replace-Once -Content $content -Pattern 'watch\(\[reviewKeyword, guidanceKeyword, messageKeyword\], \(\) => \{\r?\n  reviewPage\.value = 1;\r?\n  guidancePage\.value = 1;\r?\n  messagePage\.value = 1;\r?\n\}\);\r?\n\r?\n' -Replacement "" -Label "teacher reset watch"

  return $content
}

Update-File ".\CollegePortalView.tmp.vue" {
  param($content)

  if ($content -notmatch "useFilteredPagination") {
    $content = $content -replace 'import FilterTablePanel from "\.\./components/FilterTablePanel\.vue";', @"
import FilterTablePanel from "../components/FilterTablePanel.vue";
import { useFilteredPagination } from "../composables/useFilteredPagination";
"@
  }

  $content = Replace-Once -Content $content -Pattern 'const studentKeyword = ref\(""\);\r?\nconst teacherKeyword = ref\(""\);\r?\nconst mentorKeyword = ref\(""\);\r?\nconst organizationKeyword = ref\(""\);\r?\nconst internshipKeyword = ref\(""\);\r?\nconst archiveKeyword = ref\(""\);\r?\nconst messageKeyword = ref\(""\);\r?\n\r?\nconst studentPage = ref\(1\);\r?\nconst teacherPage = ref\(1\);\r?\nconst mentorPage = ref\(1\);\r?\nconst organizationPage = ref\(1\);\r?\nconst internshipPage = ref\(1\);\r?\nconst archivePage = ref\(1\);\r?\nconst messagePage = ref\(1\);\r?\nconst pageSize = 5;\r?\n\r?\nconst studentForm = reactive\(' -Replacement @"
const pageSize = 5;

const studentForm = reactive(
"@ -Label "college top block"

  $content = Replace-Once -Content $content -Pattern 'function containsKeyword\(values, keyword\) \{\r?\n  return values\.filter\(Boolean\)\.some\(\(value\) => String\(value\)\.includes\(keyword\)\);\r?\n\}\r?\n\r?\nconst filteredStudents = computed\(\(\) => \{.*?const pagedMessages = page\(filteredMessages, messagePage\);\r?\n' -Replacement @"
const {
  keyword: studentKeyword,
  currentPage: studentPage,
  filteredItems: filteredStudents,
  pagedItems: pagedStudents,
} = useFilteredPagination({
  source: students,
  matcher: (item) => [item.name, item.studentNo, item.major, item.className, item.internshipStatus],
  pageSize,
});
const {
  keyword: teacherKeyword,
  currentPage: teacherPage,
  filteredItems: filteredTeachers,
  pagedItems: pagedTeachers,
} = useFilteredPagination({
  source: teachers,
  matcher: (item) => [item.name, item.employeeNo, item.department, item.phone],
  pageSize,
});
const {
  keyword: mentorKeyword,
  currentPage: mentorPage,
  filteredItems: filteredMentorApplications,
  pagedItems: pagedMentorApplications,
} = useFilteredPagination({
  source: pendingMentorApplications,
  matcher: (item) => [item.student?.name, item.student?.studentNo, item.teacher?.name, item.studentRemark],
  pageSize,
});
const {
  keyword: organizationKeyword,
  currentPage: organizationPage,
  filteredItems: filteredOrganizations,
  pagedItems: pagedOrganizations,
} = useFilteredPagination({
  source: organizations,
  matcher: (item) => [item.name, item.address, item.contactName, item.contactPhone, item.nature, item.cooperationStatus],
  pageSize,
});
const {
  keyword: internshipKeyword,
  currentPage: internshipPage,
  filteredItems: filteredInternships,
  pagedItems: pagedInternships,
} = useFilteredPagination({
  source: pendingInternshipApplications,
  matcher: (item) => [item.student?.name, item.student?.studentNo, item.organization?.name, item.position, item.status],
  pageSize,
});
const {
  keyword: archiveKeyword,
  currentPage: archivePage,
  filteredItems: filteredArchives,
  pagedItems: pagedArchives,
} = useFilteredPagination({
  source: archiveForms,
  matcher: (item) => [item.studentName, item.studentNo, item.templateName, item.status, item.content?.title, item.content?.summary],
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
"@ -Label "college main block"

  $content = Replace-Once -Content $content -Pattern 'watch\(\[studentKeyword, teacherKeyword, mentorKeyword, organizationKeyword, internshipKeyword, archiveKeyword, messageKeyword\], \(\) => \{\r?\n  studentPage\.value = 1;\r?\n  teacherPage\.value = 1;\r?\n  mentorPage\.value = 1;\r?\n  organizationPage\.value = 1;\r?\n  internshipPage\.value = 1;\r?\n  archivePage\.value = 1;\r?\n  messagePage\.value = 1;\r?\n\}\);\r?\n\r?\n' -Replacement "" -Label "college reset watch"

  return $content
}
