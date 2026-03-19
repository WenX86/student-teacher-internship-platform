import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

function inList(id, segments) {
  return segments.some((segment) => id.includes(segment));
}

function manualChunks(id) {
  if (!id.includes("node_modules")) {
    return;
  }

  if (id.includes("@element-plus/icons-vue")) {
    return "vendor-ep-icons";
  }

  if (id.includes("dayjs")) {
    return "vendor-dayjs";
  }

  if (inList(id, ["@floating-ui", "@popperjs/core", "lodash-unified", "async-validator"])) {
    return "vendor-ep-deps";
  }

  if (id.includes("element-plus") || id.includes("@element-plus")) {
    return "vendor-element-plus";
  }

  if (id.includes("vue") || id.includes("pinia") || id.includes("vue-router")) {
    return "vendor-vue";
  }

  return "vendor";
}

export default defineConfig({
  plugins: [vue()],
  server: {
    host: "0.0.0.0",
    port: 5173,
  },
  build: {
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks,
      },
    },
  },
});
