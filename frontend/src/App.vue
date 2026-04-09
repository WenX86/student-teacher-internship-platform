<template>
  <el-config-provider :locale="locale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import dayjs from "dayjs";
import "dayjs/locale/zh-cn";
import zhCn from "element-plus/es/locale/lang/zh-cn";

dayjs.locale("zh-cn");

const locale = zhCn;
</script>
