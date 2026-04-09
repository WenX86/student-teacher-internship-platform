import { defineStore } from "pinia";
import { get } from "../api/http";

export const useMessageStore = defineStore("message", {
  state: () => ({
    unreadCount: 0,
  }),
  actions: {
    syncUnreadCount(count) {
      const value = Number(count || 0);
      this.unreadCount = Number.isFinite(value) && value > 0 ? value : 0;
      return this.unreadCount;
    },
    async refreshUnreadCount() {
      try {
        const dashboard = await get("/dashboard");
        return this.syncUnreadCount(dashboard?.unreadMessages || 0);
      } catch {
        return this.unreadCount;
      }
    },
    reset() {
      this.unreadCount = 0;
    },
  },
});
