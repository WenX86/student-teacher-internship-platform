<script setup>
const props = defineProps({
  keyword: {
    type: String,
    default: "",
  },
  placeholder: {
    type: String,
    default: "",
  },
  inputWidth: {
    type: String,
    default: "320px",
  },
  currentPage: {
    type: Number,
    default: 1,
  },
  total: {
    type: Number,
    default: 0,
  },
  pageSize: {
    type: Number,
    default: 5,
  },
  showFilter: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(["update:keyword", "update:currentPage"]);

function updateKeyword(value) {
  emit("update:keyword", value);
}

function updateCurrentPage(value) {
  emit("update:currentPage", value);
}
</script>

<template>
  <div class="panel-card">
    <div v-if="showFilter" class="toolbar" style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
      <el-input
        :model-value="keyword"
        :placeholder="placeholder"
        clearable
        :style="{ maxWidth: inputWidth }"
        @update:model-value="updateKeyword"
      />
      <slot name="toolbar-extra" />
    </div>
    <slot />
    <el-pagination
      v-if="total > pageSize"
      :current-page="currentPage"
      layout="prev, pager, next"
      :page-size="pageSize"
      :total="total"
      style="margin-top: 16px; justify-content: flex-end"
      @update:current-page="updateCurrentPage"
    />
  </div>
</template>
