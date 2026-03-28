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

function resolveApiUrl(url) {
  if (/^https?:\/\//.test(url)) {
    return url;
  }
  return `${API_BASE}${url}`;
}

async function fetchWithAuth(url, options = {}) {
  const hasFormDataBody = options.body instanceof FormData;
  const headers = {
    ...(hasFormDataBody || options.body === undefined ? {} : { "Content-Type": "application/json" }),
    ...(options.headers || {}),
  };

  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  try {
    return await fetch(resolveApiUrl(url), {
      ...options,
      headers,
    });
  } catch {
    throw new Error("无法连接后端服务，请确认 Spring Boot 服务已启动。");
  }
}

async function ensureSuccess(response) {
  if (response.ok) {
    return;
  }

  if (response.status === 401 || response.status === 403) {
    clearAuthState();
    redirectToLogin();
    throw new Error("登录状态已失效或当前账号无权访问，请重新登录。");
  }

  const payload = await response.clone().json().catch(() => ({}));
  throw new Error(getErrorMessage(payload));
}

export async function request(url, options = {}) {
  const response = await fetchWithAuth(url, options);
  await ensureSuccess(response);

  const payload = await response.json().catch(() => ({}));
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

export function uploadFile(file) {
  const formData = new FormData();
  formData.append("file", file);
  return request("/files/upload", {
    method: "POST",
    body: formData,
  });
}

export async function downloadFile(url, fileName) {
  const response = await fetchWithAuth(url, {
    method: "GET",
  });
  await ensureSuccess(response);

  const blob = await response.blob();
  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileName || "附件";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(objectUrl);
}

