// Module: services/api
// Purpose: Wrapper fetch/JSON và xử lý lỗi nhất quán

import { showLoading, showToast } from "../utils/dom.js";

export async function apiRequest(path, options) {
  if (options && options.showLoading !== false) {
    showLoading(true);
  }
  try {
    const res = await fetch(path, {
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...(options && options.headers ? options.headers : {})
      },
      ...options
    });
    if (res.status === 401) {
      if (!window.location.pathname.startsWith("/auth/")) {
        window.location.href = "/auth/login.html";
      }
      return null;
    }
    const text = await res.text();
    if (res.status === 204) {
      return { ok: true };
    }
    if (text === "") {
      const method = (options && options.method) ? String(options.method).toUpperCase() : "GET";
      if (method !== "GET") return { ok: true };
      return null;
    }
    if (!res.ok) {
      try {
        const errJson = JSON.parse(text);
        const msg = errJson.message || errJson.error || text;
        showToast(msg, "error");
      } catch {
        showToast(text || ("Lỗi API " + res.status), "error");
      }
      return null;
    }
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  } finally {
    if (options && options.showLoading !== false) {
      showLoading(false);
    }
  }
}
