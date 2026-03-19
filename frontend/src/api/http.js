const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080/api";

function getToken() {
  return localStorage.getItem("internship-token") || "";
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
