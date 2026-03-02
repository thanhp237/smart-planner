// Module: controllers/appController
// Purpose: Khởi động ứng dụng, session và gắn sự kiện điều hướng

import { apiRequest } from "../services/api.js";
import { showView } from "../utils/dom.js";
import { dashboardController } from "./dashboardController.js";
import { timetableController } from "./timetableController.js";
import { coursesController } from "./coursesController.js";

let currentUser = null;
window.currentWeekOffset = 0;

function updateHeaderUser(me) {
  const userEl = document.getElementById("header-user");
  const pillEl = document.getElementById("header-user-pill");
  if (!userEl || !pillEl) return;
  if (!me || typeof me !== "object") {
    userEl.textContent = "";
    pillEl.style.display = "none";
    return;
  }
  const fullName = (me.fullName || "").trim();
  const email = (me.email || "").trim();
  const display = fullName || email || "";
  userEl.textContent = display ? (": " + display) : "";
  pillEl.style.display = display ? "" : "none";
}

function updateHeader() {
  const xpEl = document.getElementById("header-xp");
  const streakEl = document.getElementById("header-streak");
  if (xpEl) xpEl.style.display = "none";
  if (streakEl) streakEl.style.display = "none";
  if (currentUser) updateHeaderUser(currentUser);
}

async function checkSession() {
  const me = await apiRequest("/api/auth/me", { method: "GET" });
  if (me === null || typeof me === "string") {
    if (!window.location.pathname.startsWith("/auth/")) {
      window.location.href = "/auth/login.html";
    }
  } else {
    currentUser = me;
    updateHeaderUser(me);
    await dashboardController.load();
    showView("dashboard");
  }
}

function initEvents() {
  const nav = document.querySelector(".sp-nav");
  if (nav) {
    nav.addEventListener("click", async e => {
      const target = e.target;
      if (!(target instanceof HTMLElement)) return;
      const btn = target.closest(".sp-nav-item");
      if (!btn) return;
      const view = btn.getAttribute("data-view");
      if (!view) return;
      showView(view);
      if (view === "dashboard") {
        await dashboardController.load();
      } else if (view === "timetable") {
        await timetableController.load();
      } else if (view === "courses") {
        await coursesController.load();
      } else if (view === "tasks") {
        // Reuse course tasks list as tasks overview if exists
        await apiRequest("/api/tasks", { method: "GET" });
      }
    });
  }
  const btnPrev = document.getElementById("btn-week-prev");
  const btnNext = document.getElementById("btn-week-next");
  if (btnPrev) {
    btnPrev.addEventListener("click", async () => {
      window.currentWeekOffset -= 1;
      await timetableController.load();
    });
  }
  if (btnNext) {
    btnNext.addEventListener("click", async () => {
      window.currentWeekOffset += 1;
      await timetableController.load();
    });
  }
  const btnGen = document.getElementById("btn-generate-timetable");
  const btnSync = document.getElementById("btn-sync-calendar");
  if (btnGen) btnGen.addEventListener("click", timetableController.generate);
  if (btnSync) btnSync.addEventListener("click", timetableController.syncWeek);
  updateHeader();
}

export const appController = {
  init: async () => {
    initEvents();
    if (timetableController.initSettings) {
      timetableController.initSettings();
    }
    await checkSession();
  }
};
