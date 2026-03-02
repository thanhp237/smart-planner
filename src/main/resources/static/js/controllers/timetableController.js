// Module: controllers/timetableController
// Purpose: Quản lý UI lịch học tuần và điều khiển phiên học

import { apiRequest } from "../services/api.js";
import { showToast, confirmDialog } from "../utils/dom.js";

const sessionStatusClass = (status) => {
  const s = (status || "").toUpperCase();
  if (s === "COMPLETED") return "sp-session-completed";
  if (s === "IN_PROGRESS") return "sp-session-in-progress";
  if (s === "SKIPPED") return "sp-session-skipped";
  return "sp-session-planned";
};

let timerInterval = null;

const getSessionDurationSeconds = (s) => {
  const minutes = typeof s?.durationMinutes === "number" ? s.durationMinutes : parseInt(String(s?.durationMinutes || ""), 10);
  if (Number.isFinite(minutes) && minutes > 0) return minutes * 60;
  const start = String(s?.startTime || "");
  const end = String(s?.endTime || "");
  const startParts = start.split(":").map(n => parseInt(n, 10));
  const endParts = end.split(":").map(n => parseInt(n, 10));
  if (startParts.length >= 2 && endParts.length >= 2 && startParts.every(Number.isFinite) && endParts.every(Number.isFinite)) {
    const startSec = startParts[0] * 3600 + startParts[1] * 60 + (startParts[2] || 0);
    const endSec = endParts[0] * 3600 + endParts[1] * 60 + (endParts[2] || 0);
    const diff = endSec - startSec;
    if (diff > 0) return diff;
  }
  return 60 * 60;
};

const getSessionEndDate = (s) => {
  const sessionDate = String(s?.sessionDate || "");
  const end = String(s?.endTime || "");
  const dateParts = sessionDate.split("-").map(n => parseInt(n, 10));
  const endParts = end.split(":").map(n => parseInt(n, 10));
  if (dateParts.length >= 3 && endParts.length >= 2 && dateParts.every(Number.isFinite) && endParts.every(Number.isFinite)) {
    return new Date(dateParts[0], dateParts[1] - 1, dateParts[2], endParts[0], endParts[1], endParts[2] || 0);
  }
  return null;
};

const getRemainingSessionSeconds = (s) => {
  const endDate = getSessionEndDate(s);
  if (endDate) {
    const remaining = Math.floor((endDate.getTime() - Date.now()) / 1000);
    if (Number.isFinite(remaining)) return Math.max(0, remaining);
  }
  return getSessionDurationSeconds(s);
};

const updateTimerDisplay = (seconds) => {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const el = document.getElementById("timer-countdown");
  if (el) {
    el.textContent = `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  }
};

async function runReflectionFlow(sessionId, hours) {
  return new Promise(resolve => {
    const overlay = document.getElementById("reflection-overlay");
    const formEl = document.getElementById("reflection-form");
    const loadingEl = document.getElementById("reflection-loading");
    const resultEl = document.getElementById("reflection-result");
    const noteEl = document.getElementById("reflection-note");
    const diffEl = document.getElementById("reflection-difficulty");
    const errEl = document.getElementById("reflection-error");
    const noteViewEl = document.getElementById("reflection-note-view");
    const qualityEl = document.getElementById("reflection-quality");
    const summaryEl = document.getElementById("reflection-summary");
    const nextEl = document.getElementById("reflection-next");
    const revisionEl = document.getElementById("reflection-revision");
    const btnCancel = document.getElementById("reflection-cancel");
    const btnSave = document.getElementById("reflection-save");
    const btnClose = document.getElementById("reflection-close");
    if (!overlay) { resolve(null); return; }
    const showForm = () => {
      formEl.style.display = "block";
      loadingEl.style.display = "none";
      resultEl.style.display = "none";
      errEl.textContent = "";
      noteEl.value = "";
      diffEl.value = "";
      if (noteViewEl) noteViewEl.textContent = "";
    };
    const showLoadingState = () => {
      formEl.style.display = "none";
      loadingEl.style.display = "block";
      resultEl.style.display = "none";
      errEl.textContent = "";
      if (noteViewEl) noteViewEl.textContent = "";
    };
    const cleanup = () => {
      overlay.style.display = "none";
      btnCancel.onclick = null;
      btnSave.onclick = null;
      btnClose.onclick = null;
    };
    overlay.style.display = "flex";
    showForm();
    btnCancel.onclick = () => { cleanup(); resolve(null); };
    btnClose.onclick = () => { cleanup(); };
    btnSave.onclick = async () => {
      const note = (noteEl.value || "").trim();
      const difficulty = (diffEl.value || "").trim();
      showLoadingState();
      const payload = { actualHoursLogged: hours, difficulty: difficulty || null, note: note || null };
      const completeResult = await apiRequest("/api/sessions/" + sessionId + "/complete", { method: "PUT", body: JSON.stringify(payload), showLoading: false });
      if (!note) { cleanup(); showToast("Đã lưu session (bỏ qua AI vì note trống).", "info"); resolve(completeResult); return; }
      let reflection = null;
      for (let i = 0; i < 12; i++) {
        reflection = await apiRequest("/api/sessions/" + sessionId + "/reflection", { method: "GET", showLoading: false });
        const status = (reflection?.aiStatus || "").toUpperCase();
        if (status === "DONE" || status === "FAILED") break;
        await new Promise(r => setTimeout(r, 1500));
      }
      if (noteViewEl) noteViewEl.textContent = reflection?.note || note;
      const status = (reflection?.aiStatus || "").toUpperCase();
      const norm = normalizeReflection(reflection, reflection?.note || note);
      qualityEl.textContent = norm.aiQualityScore ?? "";
      summaryEl.textContent = norm.aiSummary ?? "";
      nextEl.textContent = norm.aiNextAction ?? "";
      revisionEl.textContent = norm.aiRevisionSuggestion ?? "";
      if (status === "FAILED") {
        const hasContent = String(norm.aiSummary || "").trim() || String(norm.aiNextAction || "").trim() || String(norm.aiRevisionSuggestion || "").trim();
        errEl.textContent = hasContent ? "" : "AI gặp sự cố, đã hiển thị nội dung fallback.";
      } else if (status !== "DONE") {
        errEl.textContent = "AI đang phân tích...";
      } else {
        errEl.textContent = "";
      }
      formEl.style.display = "none";
      loadingEl.style.display = "none";
      resultEl.style.display = "block";
      resolve(completeResult);
    };
  });
}

function normalizeReflection(raw, noteText) {
  const d = raw || {};
  let q = d.aiQualityScore;
  let s = d.aiSummary;
  let n = d.aiNextAction;
  let r = d.aiRevisionSuggestion;
  const err = d.aiError || "";
  if ((!s || String(s).trim() === "") && err) {
    const m = err.match(/\{[\s\S]*\}/);
    if (m) {
      try {
        const j = JSON.parse(m[0]);
        if (j.summary && String(j.summary).trim()) s = j.summary;
        if (j.nextAction && String(j.nextAction).trim()) n = j.nextAction;
        if (j.revisionSuggestion && String(j.revisionSuggestion).trim()) r = j.revisionSuggestion;
        if (j.qualityScore !== undefined && j.qualityScore !== null) q = j.qualityScore;
      } catch {}
    }
    if (!s || String(s).trim() === "") {
      const ms = err.match(/"summary"\s*:\s*"([^"\n]*)/);
      if (ms && String(ms[1]).trim()) s = ms[1];
    }
    if (!n || String(n).trim() === "") {
      const mn = err.match(/"nextAction"\s*:\s*"([^"\n]*)/);
      if (mn && String(mn[1]).trim()) n = mn[1];
    }
    if (!r || String(r).trim() === "") {
      const mr = err.match(/"revisionSuggestion"\s*:\s*"([^"\n]*)/);
      if (mr && String(mr[1]).trim()) r = mr[1];
    }
    if (q === undefined || q === null || String(q).trim() === "") {
      const mq = err.match(/"qualityScore"\s*:\s*(\d+)/);
      if (mq && Number.isFinite(parseInt(mq[1], 10))) q = parseInt(mq[1], 10);
    }
  }
  if (!s || String(s).trim() === "") {
    const t = String(noteText || "").trim();
    if (t) {
      const parts = t.split(/[\.\n]/);
      s = parts[0] || t.slice(0, 160);
    }
  }
  if (!n || String(n).trim() === "") n = "Đặt lại kế hoạch và học bù buổi sau.";
  if (!r || String(r).trim() === "") r = "Ôn tập trọng tâm, chia nhỏ thời gian và hạn chế xao nhãng.";
  if (q === undefined || q === null || String(q).trim() === "") q = 0;
  return { aiQualityScore: q, aiSummary: s, aiNextAction: n, aiRevisionSuggestion: r, aiStatus: d.aiStatus, aiError: err, note: d.note };
}

async function startSession(s) {
  const endDate = getSessionEndDate(s);
  if (endDate && Date.now() >= endDate.getTime()) { showToast("Slot expired", "error"); return; }
  if (!await confirmDialog("Bắt đầu học?", "Bạn đã sẵn sàng bắt đầu môn " + s.courseName + "?")) return;
  await apiRequest("/api/sessions/" + s.id + "/start", { method: "PUT" });
  const view = document.getElementById("view-session-timer");
  document.getElementById("timer-course-name").textContent = s.courseName;
  document.getElementById("timer-task-title").textContent = s.taskTitle || "Tự học";
  view.style.display = "flex";
  const btnSkip = document.getElementById("btn-timer-skip");
  const btnComplete = document.getElementById("btn-timer-complete");
  const newSkip = btnSkip.cloneNode(true);
  btnSkip.parentNode.replaceChild(newSkip, btnSkip);
  const newComplete = btnComplete.cloneNode(true);
  btnComplete.parentNode.replaceChild(newComplete, btnComplete);
  newSkip.onclick = async () => {
    const msg = "Bạn chắc muốn bỏ phiên học này?\nViệc bỏ quá nhiều sẽ ảnh hưởng streak.";
    if (!await confirmDialog("Dừng phiên", msg, { cancelLabel: "Quay lại học", confirmLabel: "Xác nhận Skip" })) return;
    clearInterval(timerInterval);
    await apiRequest("/api/sessions/" + s.id + "/skip", { method: "PUT" });
    view.style.display = "none";
    await timetableController.load();
  };
  newComplete.onclick = async () => {
    clearInterval(timerInterval);
    const hours = parseFloat((s.durationMinutes / 60.0).toFixed(2));
    const result = await runReflectionFlow(s.id, hours);
    if (result === null) return;
    view.style.display = "none";
    await timetableController.load();
    showToast("Đã hoàn thành session!", "success");
  };
  newComplete.disabled = true;
  newComplete.style.opacity = "0.5";
  newComplete.style.cursor = "not-allowed";
  let duration = getRemainingSessionSeconds(s);
  updateTimerDisplay(duration);
  if (duration <= 0) {
    newComplete.disabled = false;
    newComplete.style.opacity = "1";
    newComplete.style.cursor = "pointer";
    document.getElementById("timer-hint").textContent = "Chúc mừng! Bạn đã hoàn thành thời gian học.";
    showToast("Hết giờ! Bạn có thể hoàn thành session ngay.", "success");
    return;
  }
  if (timerInterval) clearInterval(timerInterval);
  timerInterval = setInterval(() => {
    duration--;
    updateTimerDisplay(duration);
    if (duration <= 0) {
      clearInterval(timerInterval);
      newComplete.disabled = false;
      newComplete.style.opacity = "1";
      newComplete.style.cursor = "pointer";
      document.getElementById("timer-hint").textContent = "Chúc mừng! Bạn đã hoàn thành thời gian học.";
      showToast("Hết giờ! Bạn có thể hoàn thành session ngay.", "success");
    }
  }, 1000);
}

function resumeSession(s) {
  const view = document.getElementById("view-session-timer");
  document.getElementById("timer-course-name").textContent = s.courseName;
  document.getElementById("timer-task-title").textContent = s.taskTitle || "Tự học";
  view.style.display = "flex";
  const btnSkip = document.getElementById("btn-timer-skip");
  const btnComplete = document.getElementById("btn-timer-complete");
  const newSkip = btnSkip.cloneNode(true);
  btnSkip.parentNode.replaceChild(newSkip, btnSkip);
  const newComplete = btnComplete.cloneNode(true);
  btnComplete.parentNode.replaceChild(newComplete, btnComplete);
  newSkip.onclick = async () => {
    const msg = "Bạn chắc muốn bỏ phiên học này?\nViệc bỏ quá nhiều sẽ ảnh hưởng streak.";
    if (!await confirmDialog("Dừng phiên", msg, { cancelLabel: "Quay lại học", confirmLabel: "Xác nhận Skip" })) return;
    clearInterval(timerInterval);
    await apiRequest("/api/sessions/" + s.id + "/skip", { method: "PUT" });
    view.style.display = "none";
    await timetableController.load();
  };
  newComplete.onclick = async () => {
    clearInterval(timerInterval);
    const hours = parseFloat((getSessionDurationSeconds(s) / 3600.0).toFixed(2));
    const result = await runReflectionFlow(s.id, hours);
    if (result === null) return;
    view.style.display = "none";
    await timetableController.load();
    showToast("Đã hoàn thành session!", "success");
  };
  newComplete.disabled = true;
  newComplete.style.opacity = "0.5";
  newComplete.style.cursor = "not-allowed";
  document.getElementById("timer-hint").textContent = "Đang tiếp tục session, vui lòng hoàn thành đủ thời gian.";
  let duration = getRemainingSessionSeconds(s);
  updateTimerDisplay(duration);
  if (duration <= 0) {
    newComplete.disabled = false;
    newComplete.style.opacity = "1";
    newComplete.style.cursor = "pointer";
    document.getElementById("timer-hint").textContent = "Chúc mừng! Bạn đã hoàn thành thời gian học.";
    showToast("Hết giờ! Bạn có thể hoàn thành session ngay.", "success");
    return;
  }
  if (timerInterval) clearInterval(timerInterval);
  timerInterval = setInterval(() => {
    duration--;
    updateTimerDisplay(duration);
    if (duration <= 0) {
      clearInterval(timerInterval);
      newComplete.disabled = false;
      newComplete.style.opacity = "1";
      newComplete.style.cursor = "pointer";
      document.getElementById("timer-hint").textContent = "Chúc mừng! Bạn đã hoàn thành thời gian học.";
      showToast("Hết giờ! Bạn có thể hoàn thành session ngay.", "success");
    }
  }, 1000);
}

async function openSessionAction(s) {
  if (!s) return;
  const status = (s.status || "").toUpperCase();
  if (status === "PLANNED") startSession(s);
  else if (status === "IN_PROGRESS") resumeSession(s);
  else if (status === "COMPLETED") {
    // Fetch and display reflection viewer
    const data = await apiRequest("/api/sessions/" + s.id + "/reflection", { method: "GET" });
    // Reuse runReflectionFlow viewer to show existing reflection context (simplified)
    await runReflectionFlow(s.id, parseFloat((getSessionDurationSeconds(s) / 3600.0).toFixed(2)));
  } else if (status === "SKIPPED") {
    showToast("Session này đã bị bỏ qua.", "info");
  }
}

let settingsInitialized = false;
const prefDays = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const dayLabels = {
  1: "Thứ 2",
  2: "Thứ 3",
  3: "Thứ 4",
  4: "Thứ 5",
  5: "Thứ 6",
  6: "Thứ 7",
  7: "Chủ nhật"
};

const setActiveTab = (tabKey) => {
  const modal = document.getElementById("settings-modal");
  if (!modal) return;
  modal.querySelectorAll(".sp-tab-btn").forEach(btn => {
    btn.classList.toggle("active", btn.getAttribute("data-tab") === tabKey);
  });
  modal.querySelectorAll(".sp-tab-content").forEach(content => {
    content.classList.toggle("active", content.id === "tab-" + tabKey);
  });
};

const applyPreferenceToForm = (pref) => {
  const startEl = document.getElementById("pref-start-date");
  const endEl = document.getElementById("pref-end-date");
  const maxEl = document.getElementById("pref-max-hours");
  const blockEl = document.getElementById("pref-block-minutes");
  const allowedStr = pref?.allowedDays || "MON,TUE,WED,THU,FRI";
  const allowed = new Set(allowedStr.split(",").map(s => s.trim()).filter(Boolean));
  if (startEl) startEl.value = pref?.planStartDate || "";
  if (endEl) endEl.value = pref?.planEndDate || "";
  if (maxEl) maxEl.value = pref?.maxHoursPerDay ?? "";
  if (blockEl) blockEl.value = String(pref?.blockMinutes ?? 60);
  document.querySelectorAll("input[name='pref-days']").forEach(cb => {
    cb.checked = !allowed.has(cb.value);
  });
};

const applyDefaultPreference = () => {
  applyPreferenceToForm({
    planStartDate: "",
    planEndDate: "",
    maxHoursPerDay: 2,
    allowedDays: "MON,TUE,WED,THU,FRI",
    blockMinutes: 60
  });
};

const collectPreferenceFromForm = () => {
  const startEl = document.getElementById("pref-start-date");
  const endEl = document.getElementById("pref-end-date");
  const maxEl = document.getElementById("pref-max-hours");
  const blockEl = document.getElementById("pref-block-minutes");
  const daysOff = new Set();
  document.querySelectorAll("input[name='pref-days']").forEach(cb => {
    if (cb.checked) daysOff.add(cb.value);
  });
  const allowedDays = prefDays.filter(d => !daysOff.has(d)).join(",");
  const maxHours = parseFloat(maxEl?.value || "0");
  const blockMinutes = parseInt(blockEl?.value || "60", 10);
  const startDate = startEl?.value || null;
  const endDate = endEl?.value || null;
  if (!Number.isFinite(maxHours) || maxHours <= 0) {
    showToast("Giờ tối đa/ngày phải > 0", "error");
    return null;
  }
  if (startDate && endDate && startDate > endDate) {
    showToast("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc", "error");
    return null;
  }
  return {
    planStartDate: startDate,
    planEndDate: endDate,
    maxHoursPerDay: maxHours,
    allowedDays,
    blockMinutes
  };
};

const renderAvailabilityList = (slots) => {
  const listEl = document.getElementById("avail-list-modal");
  if (!listEl) return;
  listEl.innerHTML = "";
  const items = Array.isArray(slots) ? slots : [];
  if (items.length === 0) {
    const empty = document.createElement("div");
    empty.className = "sp-empty-state is-visible";
    empty.innerHTML = `
            <div class="sp-empty-icon">⏱️</div>
            <div class="sp-empty-title">Chưa có giờ rảnh</div>
            <div class="sp-empty-subtitle">Thêm slot để hệ thống tạo lịch.</div>
        `;
    listEl.appendChild(empty);
    return;
  }
  items.forEach(s => {
    const item = document.createElement("div");
    item.className = "sp-list-item";
    const content = document.createElement("div");
    content.className = "sp-list-content";
    const title = document.createElement("div");
    title.className = "sp-list-title";
    const dayLabel = dayLabels[s.dayOfWeek] || ("Thứ " + s.dayOfWeek);
    const start = String(s.startTime || "").slice(0, 5);
    const end = String(s.endTime || "").slice(0, 5);
    title.textContent = dayLabel + " • " + start + " - " + end;
    const meta = document.createElement("div");
    meta.className = "sp-list-meta";
    meta.textContent = s.active ? "Đang dùng" : "Tạm tắt";
    const actions = document.createElement("div");
    actions.className = "sp-list-actions";
    const btnEdit = document.createElement("button");
    btnEdit.textContent = "Sửa";
    btnEdit.className = "sp-link-button";
    btnEdit.onclick = () => fillAvailabilityForm(s);
    const btnDelete = document.createElement("button");
    btnDelete.textContent = "Xóa";
    btnDelete.className = "sp-link-button";
    btnDelete.onclick = async () => {
      if (!await confirmDialog("Xóa giờ rảnh", "Bạn có chắc muốn xóa slot này?")) return;
      await apiRequest("/api/settings/availability/" + s.id, { method: "DELETE" });
      await loadAvailability();
      showToast("Đã xóa slot", "success");
    };
    actions.append(btnEdit, btnDelete);
    content.append(title, meta);
    item.append(content, actions);
    listEl.appendChild(item);
  });
};

const fillAvailabilityForm = (slot) => {
  const idEl = document.getElementById("avail-id-modal");
  const dayEl = document.getElementById("avail-day-modal");
  const activeEl = document.getElementById("avail-active-modal");
  const startEl = document.getElementById("avail-start-modal");
  const endEl = document.getElementById("avail-end-modal");
  if (idEl) idEl.value = slot?.id ?? "";
  if (dayEl) dayEl.value = String(slot?.dayOfWeek ?? "1");
  if (activeEl) activeEl.value = String(slot?.active ?? true);
  if (startEl) startEl.value = String(slot?.startTime || "");
  if (endEl) endEl.value = String(slot?.endTime || "");
};

const clearAvailabilityForm = () => {
  fillAvailabilityForm({ id: "", dayOfWeek: 1, active: true, startTime: "", endTime: "" });
};

const loadAvailability = async () => {
  const data = await apiRequest("/api/settings/availability", { method: "GET", showLoading: false });
  renderAvailabilityList(data);
};

export const timetableController = {
  load: async () => {
    const data = await apiRequest("/api/timetable?weekOffset=" + (window.currentWeekOffset || 0), { method: "GET" });
    if (!data) return;
    const label = document.getElementById("week-label");
    if (label) label.textContent = data.weekStartDate + " - " + data.weekEndDate;
    const evalEl = document.getElementById("timetable-eval");
    if (evalEl) {
      if (data.evaluation) {
        evalEl.textContent = "Score " + data.evaluation.score + " (" + data.evaluation.level + "): " + data.evaluation.feedback;
      } else {
        evalEl.textContent = "";
      }
    }
    const grid = document.getElementById("timetable-grid");
    if (!grid) return;
    grid.innerHTML = "";
    const byDate = new Map();
    (data.sessions || []).forEach(s => {
      const key = s.sessionDate;
      if (!byDate.has(key)) byDate.set(key, []);
      byDate.get(key).push(s);
    });
    const dates = [];
    let cur = new Date(data.weekStartDate);
    for (let i = 0; i < 7; i++) {
      const y = cur.getFullYear();
      const m = String(cur.getMonth() + 1).padStart(2, "0");
      const d = String(cur.getDate()).padStart(2, "0");
      dates.push(y + "-" + m + "-" + d);
      cur.setDate(cur.getDate() + 1);
    }
    dates.forEach(dateStr => {
      const col = document.createElement("div");
      col.className = "sp-day-column day-column";
      const head = document.createElement("div");
      head.className = "sp-day-header day-header";
      const dt = new Date(dateStr);
      const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
      head.textContent = dayNames[dt.getDay()] + " " + String(dt.getDate());
      const today = new Date();
      const isToday = today.getFullYear() === dt.getFullYear() && today.getMonth() === dt.getMonth() && today.getDate() === dt.getDate();
      if (isToday) head.classList.add("is-today");
      col.appendChild(head);
      const sessions = byDate.get(dateStr) || [];
      sessions.forEach(s => {
        const div = document.createElement("div");
        div.className = "sp-session task-card " + sessionStatusClass(s.status);
        const time = document.createElement("div");
        time.className = "sp-session-time";
        time.textContent = s.startTime.slice(0,5) + " - " + s.endTime.slice(0,5);
        const title = document.createElement("div");
        title.className = "sp-session-title";
        title.textContent = s.taskTitle || "Task";
        const task = document.createElement("div");
        task.className = "sp-session-task";
        task.textContent = s.taskType || s.courseName || "";
        div.append(time, title, task);
        div.onclick = () => openSessionAction(s);
        col.appendChild(div);
      });
      grid.appendChild(col);
    });
  },
  generate: async () => {
    const pref = await apiRequest("/api/settings/preference", { method: "GET", showLoading: false });
    let blockMinutes = pref && pref.blockMinutes ? parseInt(pref.blockMinutes, 10) : 60;
    if (![30, 60, 90, 120].includes(blockMinutes)) blockMinutes = 60;
    if (!confirm("Bạn có muốn tạo lịch cho TOÀN BỘ lộ trình (đến ngày kết thúc kế hoạch) không?\n\nChọn OK để tạo tất cả các tuần.\nChọn Cancel để chỉ tạo tuần hiện tại.")) {
      await apiRequest("/api/timetable/generate?weekOffset=" + (window.currentWeekOffset || 0) + "&blockMinutes=" + blockMinutes, { method: "POST" });
    } else {
      showToast("Đang tạo lịch cho toàn bộ lộ trình, vui lòng chờ...", "info");
      await apiRequest("/api/timetable/generate-all?blockMinutes=" + blockMinutes, { method: "POST" });
      showToast("Đã tạo xong lịch cho toàn bộ lộ trình!", "success");
    }
    await timetableController.load();
  },
  syncWeek: async () => {
    await apiRequest("/api/calendar/sync-week", { method: "POST" });
    showToast("Đã sync calendar cho tuần này", "success");
  },
  initSettings: () => {
    if (settingsInitialized) return;
    settingsInitialized = true;
    const modal = document.getElementById("settings-modal");
    const btnOpen = document.getElementById("btn-open-settings");
    const btnClose = document.getElementById("btn-settings-close");
    const btnSave = document.getElementById("btn-settings-save");
    const btnReset = document.getElementById("btn-settings-reset");
    const btnAddAvail = document.getElementById("btn-avail-add-new");
    const btnCancelAvail = document.getElementById("btn-avail-cancel-modal");
    const availForm = document.getElementById("avail-form-modal");
    if (!modal) return;
    if (btnOpen) {
      btnOpen.addEventListener("click", async () => {
        modal.style.display = "flex";
        setActiveTab("general");
        const pref = await apiRequest("/api/settings/preference", { method: "GET", showLoading: false });
        if (pref) applyPreferenceToForm(pref);
        await loadAvailability();
      });
    }
    if (btnClose) btnClose.addEventListener("click", () => { modal.style.display = "none"; });
    modal.querySelectorAll(".sp-tab-btn").forEach(btn => {
      btn.addEventListener("click", () => setActiveTab(btn.getAttribute("data-tab")));
    });
    if (btnSave) {
      btnSave.addEventListener("click", async () => {
        const payload = collectPreferenceFromForm();
        if (!payload) return;
        const res = await apiRequest("/api/settings/preference", { method: "PUT", body: JSON.stringify(payload) });
        if (res) showToast("Đã lưu cấu hình", "success");
      });
    }
    if (btnReset) {
      btnReset.addEventListener("click", () => {
        applyDefaultPreference();
      });
    }
    if (btnAddAvail) {
      btnAddAvail.addEventListener("click", () => {
        clearAvailabilityForm();
      });
    }
    if (btnCancelAvail) {
      btnCancelAvail.addEventListener("click", () => {
        clearAvailabilityForm();
      });
    }
    if (availForm) {
      availForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const idEl = document.getElementById("avail-id-modal");
        const dayEl = document.getElementById("avail-day-modal");
        const activeEl = document.getElementById("avail-active-modal");
        const startEl = document.getElementById("avail-start-modal");
        const endEl = document.getElementById("avail-end-modal");
        const id = idEl?.value || "";
        const day = parseInt(dayEl?.value || "1", 10);
        const start = startEl?.value || "";
        const end = endEl?.value || "";
        const active = activeEl?.value === "true";
        if (!start || !end) { showToast("Vui lòng chọn giờ bắt đầu/kết thúc", "error"); return; }
        if (start >= end) { showToast("Giờ bắt đầu phải trước giờ kết thúc", "error"); return; }
        const body = { dayOfWeek: day, startTime: start, endTime: end, active };
        let path = "/api/settings/availability";
        let method = "POST";
        if (id) { path = "/api/settings/availability/" + id; method = "PUT"; }
        const res = await apiRequest(path, { method, body: JSON.stringify(body) });
        if (!res && method === "POST") return;
        clearAvailabilityForm();
        await loadAvailability();
        showToast("Đã lưu slot", "success");
      });
    }
  }
};
