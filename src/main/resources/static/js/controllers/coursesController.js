// Module: controllers/coursesController
// Purpose: Quản lý UI danh sách môn và công việc theo môn

import { apiRequest } from "../services/api.js";
import { showToast, confirmDialog } from "../utils/dom.js";

async function renderCourseTasksList(courseId) {
  const listEl = document.getElementById("course-tasks-list");
  if (!listEl) return;
  listEl.innerHTML = "";
  const all = await apiRequest("/api/tasks", { method: "GET", showLoading: false });
  const tasks = Array.isArray(all) ? all.filter(t => String(t.courseId) === String(courseId)) : [];
  if (tasks.length === 0) {
    const empty = document.createElement("div");
    empty.className = "sp-empty-state is-visible";
    empty.innerHTML = `
            <div class="sp-empty-icon">📝</div>
            <div class="sp-empty-title">Chưa có công việc cho môn này</div>
            <div class="sp-empty-subtitle">Thêm task để bắt đầu kế hoạch.</div>
        `;
    listEl.appendChild(empty);
    return;
  }
  tasks.forEach(t => {
    const item = document.createElement("div");
    item.className = "sp-list-item";
    const content = document.createElement("div");
    content.className = "sp-list-content";
    const title = document.createElement("div");
    title.className = "sp-list-title";
    title.textContent = t.title + " [" + t.priority + "]";
    const meta = document.createElement("div");
    meta.className = "sp-list-meta";
    meta.textContent = "#" + t.id + " • " + (t.deadlineDate || "") + " • " + t.status;
    const actions = document.createElement("div");
    actions.className = "sp-list-actions";
    const btnEdit = document.createElement("button");
    btnEdit.textContent = "Sửa";
    btnEdit.className = "sp-link-button";
    btnEdit.onclick = () => {
      document.getElementById("course-task-id").value = t.id;
      document.getElementById("course-task-title").value = t.title || "";
      document.getElementById("course-task-type").value = t.type || "OTHER";
      document.getElementById("course-task-priority").value = t.priority || "MEDIUM";
      document.getElementById("course-task-deadline").value = t.deadlineDate || "";
      document.getElementById("course-task-estimated-hours").value = t.estimatedHours ?? 0;
      document.getElementById("course-task-remaining-hours").value = t.remainingHours ?? t.estimatedHours ?? 0;
      document.getElementById("course-task-remaining-wrap").classList.remove("is-hidden");
      document.getElementById("course-task-status").value = t.status || "OPEN";
      document.getElementById("course-task-status-wrap").classList.remove("is-hidden");
      document.getElementById("course-task-description").value = t.description || "";
      document.getElementById("course-task-form").classList.remove("is-hidden");
      const saveBtn = document.getElementById("course-task-save");
      if (saveBtn) saveBtn.style.display = "";
      const newBtn = document.getElementById("course-task-new");
      if (newBtn) {
        newBtn.textContent = "+ Task mới";
        newBtn.style.display = "none"; // hide "+ Task mới" when editing
      }
    };
    const btnDelete = document.createElement("button");
    btnDelete.textContent = "Xóa";
    btnDelete.className = "sp-link-button";
    btnDelete.onclick = async () => {
      if (!await confirmDialog("Xóa công việc", "Bạn có chắc muốn xóa task #" + t.id + "?")) return;
      await apiRequest("/api/tasks/" + t.id, { method: "DELETE" });
      await renderCourseTasksList(courseId);
      showToast("Đã xóa công việc", "success");
    };
    actions.append(btnEdit, btnDelete);
    content.append(title, meta);
    item.append(content, actions);
    listEl.appendChild(item);
  });
}

async function openCourseTasks(course) {
  const overlay = document.getElementById("course-tasks-modal");
  const titleEl = document.getElementById("course-tasks-title");
  const listEl = document.getElementById("course-tasks-list");
  const formEl = document.getElementById("course-task-form");
  const btnNew = document.getElementById("course-task-new");
  const btnSave = document.getElementById("course-task-save");
  const btnCancel = document.getElementById("course-task-cancel");
  const errEl = document.getElementById("course-task-error");
  if (!overlay || !titleEl || !listEl || !formEl || !btnNew || !btnSave || !btnCancel) return;
  titleEl.textContent = course.name;
  errEl.textContent = "";
  formEl.classList.add("is-hidden");
  await renderCourseTasksList(course.id);
  overlay.style.display = "flex";
  btnSave.style.display = "none";
  btnNew.textContent = "+ Task mới";
  btnNew.onclick = async () => {
    errEl.textContent = "";
    const idVal = document.getElementById("course-task-id").value;
    const isFormHidden = formEl.classList.contains("is-hidden");
    const isNewMode = !idVal;
    if (isFormHidden || !isNewMode) {
      formEl.classList.remove("is-hidden");
      document.getElementById("course-task-id").value = "";
      document.getElementById("course-task-title").value = "";
      document.getElementById("course-task-type").value = "OTHER";
      document.getElementById("course-task-priority").value = "MEDIUM";
      document.getElementById("course-task-status").value = "OPEN";
      document.getElementById("course-task-status-wrap").classList.add("is-hidden");
      document.getElementById("course-task-deadline").value = "";
      document.getElementById("course-task-estimated-hours").value = "";
      document.getElementById("course-task-remaining-hours").value = "";
      document.getElementById("course-task-remaining-wrap").classList.add("is-hidden");
      document.getElementById("course-task-description").value = "";
      btnSave.style.display = "none";
      return;
    }
    const title = document.getElementById("course-task-title").value.trim();
    const type = document.getElementById("course-task-type").value;
    const priority = document.getElementById("course-task-priority").value;
    const deadline = document.getElementById("course-task-deadline").value;
    const est = parseFloat(document.getElementById("course-task-estimated-hours").value || "0");
    const desc = document.getElementById("course-task-description").value;
    if (!title) { errEl.textContent = "Vui lòng nhập tiêu đề"; return; }
    if (!deadline) { errEl.textContent = "Vui lòng chọn deadline"; return; }
    if (!Number.isFinite(est) || est <= 0) { errEl.textContent = "Vui lòng nhập tổng giờ hợp lệ (> 0)"; return; }
    const body = { courseId: course.id, title, description: desc, type, priority, deadlineDate: deadline, estimatedHours: est };
    const res = await apiRequest("/api/tasks", { method: "POST", body: JSON.stringify(body) });
    if (!res) { errEl.textContent = "Không lưu được task"; return; }
    formEl.classList.add("is-hidden");
    await renderCourseTasksList(course.id);
    showToast("Đã tạo công việc", "success");
  };
  btnSave.onclick = async () => {
    errEl.textContent = "";
    const id = document.getElementById("course-task-id").value;
    const title = document.getElementById("course-task-title").value.trim();
    const type = document.getElementById("course-task-type").value;
    const priority = document.getElementById("course-task-priority").value;
    const status = document.getElementById("course-task-status").value;
    const deadline = document.getElementById("course-task-deadline").value;
    const est = parseFloat(document.getElementById("course-task-estimated-hours").value || "0");
    const rem = parseFloat(document.getElementById("course-task-remaining-hours").value || String(est));
    const desc = document.getElementById("course-task-description").value;
    if (!title) { errEl.textContent = "Vui lòng nhập tiêu đề"; return; }
    if (!deadline) { errEl.textContent = "Vui lòng chọn deadline"; return; }
    if (!Number.isFinite(est) || est <= 0) { errEl.textContent = "Vui lòng nhập tổng giờ hợp lệ (> 0)"; return; }
    if (id) {
      if (!Number.isFinite(rem) || rem < 0 || rem > est) { errEl.textContent = "Giờ còn lại phải từ 0 đến tổng giờ"; return; }
    }
    const base = { courseId: course.id, title, description: desc, type, priority, deadlineDate: deadline, estimatedHours: est };
    let path = "/api/tasks";
    let method = "POST";
    let body = base;
    if (id) { path = "/api/tasks/" + id; method = "PUT"; body = { ...base, remainingHours: rem, status }; }
    const res = await apiRequest(path, { method, body: JSON.stringify(body) });
    if (!res) { errEl.textContent = "Không lưu được task"; return; }
    formEl.classList.add("is-hidden");
    await renderCourseTasksList(course.id);
    showToast("Đã lưu công việc", "success");
  };
  btnCancel.onclick = () => { overlay.style.display = "none"; };
}

async function loadCourses() {
  const list = document.getElementById("courses-list");
  if (!list) return;
  list.innerHTML = "";
  const data = await apiRequest("/api/courses", { method: "GET" });
  if (!Array.isArray(data)) return;
  const emptyEl = document.getElementById("courses-empty");
  if (emptyEl) emptyEl.classList.toggle("is-visible", data.length === 0);
  data.forEach(c => {
    const item = document.createElement("div");
    item.className = "sp-list-item";
    const content = document.createElement("div");
    content.className = "sp-list-content";
    const title = document.createElement("div");
    title.className = "sp-list-title";
    title.textContent = c.name + " (" + c.priority + ")";
    const meta = document.createElement("div");
    meta.className = "sp-list-meta";
    meta.textContent = "#" + c.id + " • " + (c.deadlineDate || "") + " • " + c.status;
    const actions = document.createElement("div");
    actions.className = "sp-list-actions";
    const btnTasks = document.createElement("button");
    btnTasks.textContent = "Công việc";
    btnTasks.className = "sp-button sp-button-secondary sp-btn-sm";
    btnTasks.onclick = () => openCourseTasks(c);
    const btnEdit = document.createElement("button");
    btnEdit.textContent = "Sửa";
    btnEdit.className = "sp-link-button";
    btnEdit.onclick = () => openCourseForm(c);
    const btnDelete = document.createElement("button");
    btnDelete.textContent = "Xóa";
    btnDelete.className = "sp-link-button";
    btnDelete.onclick = () => deleteCourse(c.id);
    actions.append(btnTasks, btnEdit, btnDelete);
    content.append(title, meta);
    item.append(content, actions);
    list.appendChild(item);
  });
}

export const coursesController = {
  load: loadCourses,
  openCourseTasks
};

// Course form functions (add/edit)
function toggleCourseForm(show) {
  const card = document.getElementById("course-form-card");
  const errorEl = document.getElementById("course-form-error");
  if (!card) return;
  card.style.display = show ? "block" : "none";
  if (!show && errorEl) errorEl.textContent = "";
}

function openCourseForm(course) {
  const title = document.getElementById("course-form-title");
  const idEl = document.getElementById("course-id");
  const nameEl = document.getElementById("course-name");
  const priEl = document.getElementById("course-priority");
  const deadlineEl = document.getElementById("course-deadline");
  const hoursEl = document.getElementById("course-hours");
  const statusWrap = document.getElementById("course-status-wrapper");
  const statusEl = document.getElementById("course-status");
  if (!nameEl || !priEl || !hoursEl) return;
  if (course) {
    title.textContent = "Sửa môn học";
    idEl.value = course.id;
    nameEl.value = course.name || "";
    priEl.value = course.priority || "MEDIUM";
    deadlineEl.value = course.deadlineDate || "";
    hoursEl.value = course.totalHours ?? 0;
    if (statusWrap) statusWrap.style.display = "block";
    if (statusEl) statusEl.value = course.status || "ACTIVE";
  } else {
    title.textContent = "Thêm môn học";
    idEl.value = "";
    nameEl.value = "";
    priEl.value = "MEDIUM";
    deadlineEl.value = "";
    hoursEl.value = "";
    if (statusWrap) statusWrap.style.display = "none";
  }
  toggleCourseForm(true);
}

async function handleCourseSubmit(e) {
  e.preventDefault();
  const id = document.getElementById("course-id").value;
  const name = document.getElementById("course-name").value.trim();
  const priority = document.getElementById("course-priority").value;
  const deadline = document.getElementById("course-deadline").value || null;
  const hours = parseFloat(document.getElementById("course-hours").value || "0");
  const statusWrap = document.getElementById("course-status-wrapper");
  let status = "ACTIVE";
  const statusEl = document.getElementById("course-status");
  if (statusWrap && statusEl && statusWrap.style && statusWrap.style.display === "block") {
    status = statusEl.value;
  }
  const errorEl = document.getElementById("course-form-error");
  if (errorEl) errorEl.textContent = "";
  const body = { name, priority, deadlineDate: deadline, totalHours: hours };
  let path = "/api/courses";
  let method = "POST";
  if (id) {
    path = "/api/courses/" + id;
    method = "PUT";
    body.status = status;
  }
  const res = await apiRequest(path, { method, body: JSON.stringify(body) });
  if (!res) {
    if (errorEl) errorEl.textContent = "Không lưu được môn học";
    return;
  }
  toggleCourseForm(false);
  await loadCourses();
  showToast("Đã lưu môn học", "success");
}

async function deleteCourse(id) {
  if (!await confirmDialog("Xóa môn học", "Bạn có chắc muốn xóa môn học #" + id + "?")) return;
  await apiRequest("/api/courses/" + id, { method: "DELETE" });
  await loadCourses();
  showToast("Đã xóa môn học", "success");
}

// Bind buttons on Courses view
document.addEventListener("DOMContentLoaded", () => {
  const btnCourseNew = document.getElementById("btn-course-new");
  if (btnCourseNew) btnCourseNew.addEventListener("click", () => openCourseForm(null));
  const courseCancel = document.getElementById("course-form-cancel");
  if (courseCancel) courseCancel.addEventListener("click", () => toggleCourseForm(false));
  const courseForm = document.getElementById("course-form");
  if (courseForm) courseForm.addEventListener("submit", handleCourseSubmit);
});
