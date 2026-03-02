// Module: controllers/dashboardController
// Purpose: Hiển thị tổng quan và deadlines

import { apiRequest } from "../services/api.js";

function createKpi(label, value) {
  const box = document.createElement("div");
  box.className = "sp-kpi stat-card no-icon";
  const right = document.createElement("div");
  right.style.display = "flex";
  right.style.flexDirection = "column";
  const l = document.createElement("div");
  l.className = "sp-kpi-label";
  l.textContent = label;
  const v = document.createElement("div");
  v.className = "sp-kpi-value";
  v.textContent = value ?? 0;
  right.append(l, v);
  box.append(right);
  return box;
}

function renderDashboardOverview(overview, deadlines) {
  const tasksEl = document.getElementById("overview-tasks");
  const coursesEl = document.getElementById("overview-courses");
  const hoursEl = document.getElementById("overview-hours");
  const deadlinesEl = document.getElementById("overview-deadlines");
  const t = (overview && overview.taskSummary) ? overview.taskSummary : { total: 0, open: 0, done: 0, overdue: 0 };
  const c = (overview && overview.courseSummary) ? overview.courseSummary : { total: 0, active: 0, archived: 0 };
  const h = (overview && overview.studyHours) ? overview.studyHours : { plannedThisWeek: 0, actualThisWeek: 0, completionRate: 0 };
  tasksEl.innerHTML = "";
  coursesEl.innerHTML = "";
  hoursEl.innerHTML = "";
  deadlinesEl.innerHTML = "";
  tasksEl.append(createKpi("Tổng task", t.total), createKpi("Đang mở", t.open), createKpi("Hoàn thành", t.done));
  coursesEl.append(createKpi("Môn học", c.total), createKpi("Đang học", c.active), createKpi("Đã lưu trữ", c.archived));
  const planned = h.plannedThisWeek ?? 0;
  const actual = h.actualThisWeek ?? 0;
  const rate = h.completionRate ?? 0;
  hoursEl.append(createKpi("Giờ planned", planned), createKpi("Giờ thực tế", actual), createKpi("Hoàn thành", rate + " %"));
  if (window.lucide && typeof window.lucide.createIcons === "function") {
    window.lucide.createIcons({ attrs: { width: 24, height: 24, stroke: "currentColor" } });
  }
  const list = Array.isArray(deadlines) ? deadlines : [];
  list.forEach(d => {
    const item = document.createElement("div");
    item.className = "deadline-item hover-lift";
    if ((d.daysRemaining ?? 0) <= 2) item.classList.add("deadline-danger");
    const left = document.createElement("div");
    left.innerHTML = "<strong>" + (d.taskTitle || "") + "</strong><br><span class='sp-text-secondary'>" + (d.courseName || "") + "</span>";
    const right = document.createElement("div");
    right.textContent = "Còn " + (d.daysRemaining ?? 0) + " ngày";
    item.append(left, right);
    deadlinesEl.appendChild(item);
  });
  const empty = document.getElementById("overview-deadlines-empty");
  if (empty) empty.classList.toggle("is-visible", list.length === 0);
}

export const dashboardController = {
  load: async () => {
    const [overview, deadlines] = await Promise.all([
      apiRequest("/api/dashboard/overview", { method: "GET" }),
      apiRequest("/api/dashboard/deadlines?limit=4", { method: "GET" })
    ]);
    renderDashboardOverview(overview, deadlines);
  }
};
