// Module: utils/dom
// Purpose: Thao tác DOM dùng chung và UI helpers

export const showView = (name) => {
  const views = ["dashboard", "timetable", "courses", "tasks"];
  views.forEach(v => {
    const el = document.getElementById("view-" + v);
    if (el) {
      el.classList.toggle("sp-view-active", v === name);
      el.style.display = v === name ? "block" : "none";
    }
  });
  const navItems = document.querySelectorAll(".sp-nav-item");
  navItems.forEach(btn => {
    const view = btn.getAttribute("data-view");
    const isActive = view === name;
    btn.classList.toggle("sp-nav-item-active", isActive);
    btn.classList.toggle("active", isActive);
  });
};

export const showLoading = (show) => {
  const el = document.getElementById("loading-overlay");
  if (el) el.style.display = show ? "flex" : "none";
};

export const showToast = (msg, type = "info") => {
  const container = document.getElementById("toast-container");
  if (!container) return;
  const toast = document.createElement("div");
  toast.className = "toast";
  toast.innerHTML = msg;
  if (type === "error") toast.classList.add("toast-error");
  else if (type === "success") toast.classList.add("toast-success");
  else if (type === "reward") toast.classList.add("toast-reward");
  container.appendChild(toast);
  requestAnimationFrame(() => toast.classList.add("show"));
  setTimeout(() => {
    toast.classList.remove("show");
    setTimeout(() => toast.remove(), 300);
  }, 4000);
};

export const confirmDialog = (title, message, options) => {
  return new Promise(resolve => {
    const overlay = document.getElementById("modal-overlay");
    const titleEl = document.getElementById("modal-title");
    const msgEl = document.getElementById("modal-message");
    const btnOk = document.getElementById("modal-confirm");
    const btnCancel = document.getElementById("modal-cancel");
    if (!overlay) {
      resolve(window.confirm(message));
      return;
    }
    titleEl.textContent = title;
    msgEl.textContent = message;
    const defaultCancel = btnCancel.textContent;
    const defaultOk = btnOk.textContent;
    if (options && typeof options === "object") {
      if (options.cancelLabel) btnCancel.textContent = options.cancelLabel;
      if (options.confirmLabel) btnOk.textContent = options.confirmLabel;
    }
    overlay.style.display = "flex";
    const cleanup = () => {
      overlay.style.display = "none";
      btnOk.onclick = null;
      btnCancel.onclick = null;
      btnCancel.textContent = defaultCancel;
      btnOk.textContent = defaultOk;
    };
    btnOk.onclick = () => { cleanup(); resolve(true); };
    btnCancel.onclick = () => { cleanup(); resolve(false); };
  });
};
