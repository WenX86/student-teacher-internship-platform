import { defineStore } from "pinia";
import { get, post } from "../api/http";

const STORAGE_TOKEN = "internship-token";
const STORAGE_USER = "internship-user";

function persistUser(user) {
  localStorage.setItem(STORAGE_USER, JSON.stringify(user));
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: localStorage.getItem(STORAGE_TOKEN) || "",
    user: JSON.parse(localStorage.getItem(STORAGE_USER) || "null"),
  }),
  actions: {
    async login(form) {
      const result = await post("/auth/login", form);
      this.token = result.token;
      this.user = result.user;
      localStorage.setItem(STORAGE_TOKEN, result.token);
      persistUser(result.user);
      return result.user;
    },
    async refreshUser() {
      if (!this.token) {
        return null;
      }

      const user = await get("/auth/me");
      this.user = user;
      persistUser(user);
      return user;
    },
    async changePassword(newPassword) {
      await post("/auth/change-password", { newPassword });
      return this.refreshUser();
    },
    logout() {
      this.token = "";
      this.user = null;
      localStorage.removeItem(STORAGE_TOKEN);
      localStorage.removeItem(STORAGE_USER);
    },
  },
});
