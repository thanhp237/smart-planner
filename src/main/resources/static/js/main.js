// Entry: main.js
// Purpose: Khởi động ứng dụng theo cấu trúc module

import { appController } from "./controllers/appController.js";
import { dashboardController } from "./controllers/dashboardController.js";

window.addEventListener("DOMContentLoaded", async () => {
  await appController.init();
  const dash = document.getElementById("view-dashboard");
  if (dash && dash.classList.contains("sp-view-active")) {
    try {
      await dashboardController.load();
    } catch {}
  }
});
