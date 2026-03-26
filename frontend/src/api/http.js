const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080/api";
const STORAGE_TOKEN = "internship-token";
const STORAGE_USER = "internship-user";

function getToken() {
  return localStorage.getItem(STORAGE_TOKEN) || "";
}

function clearAuthState() {
  localStorage.removeItem(STORAGE_TOKEN);
  localStorage.removeItem(STORAGE_USER);
}

function redirectToLogin() {
  if (typeof window === "undefined") {
    return;
  }

  if (window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
}

function getErrorMessage(payload) {
  if (typeof payload?.message === "string" && payload.message.trim()) {
    return payload.message;
  }

  if (typeof payload?.error === "string" && payload.error.trim()) {
    return payload.error;
  }

  if (typeof payload?.data === "string" && payload.data.trim()) {
    return payload.data;
  }

  return "请求失败";
}

export async function request(url, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };

  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(`${API_BASE}${url}`, {
      ...options,
      headers,
    });
  } catch {
    throw new Error("无法连接后端服务，请确认 Spring Boot 服务已启动。");
  }

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      clearAuthState();
      redirectToLogin();
      throw new Error("登录状态已失效或当前账号无权访问，请重新登录。");
    }

    throw new Error(getErrorMessage(payload));
  }

  return payload.data;
}

export function get(url) {
  return request(url);
}

export function post(url, body) {
  return request(url, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function put(url, body) {
  return request(url, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function patch(url, body) {
  return request(url, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}
