"use strict";

const el = (tag, text = "", className = "") => {
  const item = document.createElement(tag);
  if (text !== "") item.textContent = String(text);
  if (className) item.className = className;
  return item;
};

const state = { user: null, page: "overview", employees: [], reviews: [], goals: [], selectedReview: 0, selectedGoal: 0, selectedEmployee: 0, reviewFilter: "0" };
const byId = id => document.getElementById(id);
const view = byId("view");
const appMessage = byId("app-message");
const STATUS_OPTIONS = ["Not started", "In progress", "At risk", "Completed"];

async function api(path, options = {}) {
  const headers = { "X-ApexInsight-Request": "1", ...(options.body ? { "Content-Type": "application/json" } : {}), ...(options.headers || {}) };
  const response = await fetch(path, { credentials: "same-origin", ...options, headers });
  const result = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new Error(result?.error || `Request failed (${response.status}).`);
  return result;
}

function notify(message = "", error = false) {
  appMessage.textContent = message;
  appMessage.style.color = error ? "#a13e47" : "";
}

function showLogin() {
  byId("app").hidden = true;
  byId("login-screen").hidden = false;
  byId("login-message").textContent = "";
  byId("login-username").focus();
}

function showApp() {
  byId("login-screen").hidden = true;
  byId("app").hidden = false;
  byId("current-user").textContent = `${state.user.fullName} · ${state.user.role === "MANAGER" ? "Manager" : state.user.jobTitle}`;
  drawNavigation();
  openPage("overview");
}

function drawNavigation() {
  const navigation = byId("navigation");
  navigation.replaceChildren();
  const pages = [["overview", "Overview"], ["reviews", state.user.role === "MANAGER" ? "Reviews" : "My reviews"],
    ["goals", state.user.role === "MANAGER" ? "Goals" : "My goals"]];
  if (state.user.role === "MANAGER") pages.push(["team", "Team"]);
  pages.push(["account", "Account"]);
  for (const [key, label] of pages) {
    const button = el("button", label, `nav-button${state.page === key ? " active" : ""}`);
    button.type = "button";
    button.setAttribute("aria-current", state.page === key ? "page" : "false");
    button.addEventListener("click", () => openPage(key));
    navigation.append(button);
  }
}

async function openPage(page) {
  state.page = page;
  state.selectedReview = 0;
  state.selectedGoal = 0;
  drawNavigation();
  const labels = {
    overview: ["Overview", "A current snapshot of feedback, goals, and team progress."],
    reviews: [state.user.role === "MANAGER" ? "Performance reviews" : "Your performance", "Thoughtful feedback and a clear record of progress."],
    goals: [state.user.role === "MANAGER" ? "Goals & progress" : "Your goals", "Clear outcomes, due dates, and measurable milestones."],
    team: ["Team accounts", "Manage employee profiles and account access."],
    account: ["Account security", "Update your password and keep your account secure."]
  };
  byId("page-title").textContent = labels[page][0];
  byId("page-subtitle").textContent = labels[page][1];
  notify("");
  view.replaceChildren(el("div", "Loading your workspace…", "empty-state"));
  try {
    if (state.user.role === "MANAGER" && page !== "account") {
      state.employees = await api("/api/employees");
    }
    if (page === "overview") await drawOverview();
    else if (page === "reviews") await drawReviews();
    else if (page === "goals") await drawGoals();
    else if (page === "team" && state.user.role === "MANAGER") drawTeam();
    else if (page === "account") drawAccount();
    else throw new Error("That page is unavailable for your account.");
  } catch (error) {
    view.replaceChildren(el("div", error.message, "empty-state"));
    notify(error.message, true);
    if (error.message.includes("sign in")) { state.user = null; showLogin(); }
  }
}

function metric(label, value) {
  const card = el("article", "", "metric");
  card.append(el("div", label, "metric-label"), el("div", value, "metric-value"));
  return card;
}

async function drawOverview() {
  const [reviews, goals] = await Promise.all([
    api(state.user.role === "MANAGER" ? "/api/reviews" : "/api/reviews"),
    api("/api/goals")
  ]);
  state.reviews = reviews;
  state.goals = goals;
  const average = reviews.length ? (reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length).toFixed(1) : "—";
  const outstanding = goals.filter(goal => goal.progress < 100).length;
  const cards = state.user.role === "MANAGER"
    ? [["Team members", state.employees.length], ["Reviews written", reviews.length], ["Goals in progress", outstanding]]
    : [["Average rating", reviews.length ? `${average} / 5` : "—"], ["Reviews received", reviews.length],
      ["Goals completed", `${goals.filter(goal => goal.progress === 100).length} of ${goals.length}`]];
  const metrics = el("section", "", "metric-grid");
  for (const [label, value] of cards) metrics.append(metric(label, value));
  const panel = el("section", "", "panel");
  const heading = el("div", "", "section-head");
  heading.append(el("h2", "Recent activity", "section-heading"), button("Refresh overview", "secondary", async () => openPage("overview")));
  const list = el("div", "", "activity-list");
  const activities = [
    ...reviews.map(review => ({ date: review.createdAt.slice(0, 10), title: `Performance review · ${review.rating} / 5`,
      note: state.user.role === "MANAGER" ? review.employeeName : review.managerName })),
    ...goals.map(goal => ({ date: goal.dueDate, title: `Goal · ${goal.progress}%`, note: `${state.user.role === "MANAGER" ? goal.employeeName + " · " : ""}${goal.title}` }))
  ].sort((left, right) => right.date.localeCompare(left.date)).slice(0, 12);
  if (!activities.length) list.append(el("p", "There’s no activity yet. Your updates will appear here.", "empty"));
  for (const activity of activities) {
    const row = el("div", "", "activity-item");
    row.append(el("span", activity.date, "activity-date"), el("span", activity.title, "activity-title"),
      el("span", activity.note, "activity-note"));
    list.append(row);
  }
  panel.append(heading, list);
  view.replaceChildren(metrics, panel);
}

function button(label, variant, action) {
  const control = el("button", label, `button ${variant || "secondary"}`);
  control.type = "button";
  control.addEventListener("click", action);
  return control;
}

function tableSection(headings, records, renderRow, searchHint = "Search this table") {
  const section = el("section", "", "panel");
  const controls = el("div", "", "section-head");
  controls.append(el("h2", "Records", "section-heading"));
  const toolbar = el("div", "", "toolbar");
  const search = el("input", "", "search-input");
  search.type = "search";
  search.placeholder = searchHint;
  search.setAttribute("aria-label", searchHint);
  toolbar.append(search);
  controls.append(toolbar);
  const wrap = el("div", "", "table-wrap");
  const table = document.createElement("table");
  const head = document.createElement("thead");
  const headerRow = document.createElement("tr");
  for (const heading of headings) headerRow.append(el("th", heading));
  head.append(headerRow);
  const body = document.createElement("tbody");
  const populate = filter => {
    const query = filter.trim().toLocaleLowerCase();
    const filtered = records.filter(record => !query || Object.values(record).some(value =>
      String(value ?? "").toLocaleLowerCase().includes(query)));
    body.replaceChildren();
    if (!filtered.length) {
      const row = document.createElement("tr");
      const empty = el("td", records.length ? "No matching records." : "Nothing here yet.", "empty");
      empty.colSpan = headings.length;
      row.append(empty);
      body.append(row);
      return;
    }
    for (const record of filtered) body.append(renderRow(record));
  };
  search.addEventListener("input", () => populate(search.value));
  populate("");
  table.append(head, body);
  wrap.append(table);
  section.append(controls, wrap);
  return section;
}

function rowWith(values) {
  const row = document.createElement("tr");
  for (const value of values) {
    const cell = document.createElement("td");
    if (value instanceof Node) cell.append(value);
    else cell.textContent = String(value ?? "—");
    row.append(cell);
  }
  return row;
}

function actions(...buttons) {
  const wrap = el("div", "", "row-actions");
  for (const control of buttons) wrap.append(control);
  return wrap;
}

async function drawReviews() {
  const manager = state.user.role === "MANAGER";
  const employeeFilter = manager ? state.reviewFilter : null;
  state.reviews = await api(manager && employeeFilter && employeeFilter !== "0"
    ? `/api/reviews?employeeId=${encodeURIComponent(employeeFilter)}` : "/api/reviews");
  const panel = el("section", "", "panel");
  if (manager) {
    const split = el("div", "", "split");
    const editor = el("section", "", "panel");
    editor.append(el("h2", "Write a review"), el("p", "Select an employee and share specific, actionable feedback.", "panel-description"));
    const form = el("form");
    form.id = "review-form";
    const employee = selectField("Employee", state.employees.map(person => [person.id, `${person.fullName} · ${person.jobTitle}`]), "review-employee");
    const rating = selectField("Rating", [[5, "5 · Exceptional"], [4, "4 · Exceeds expectations"], [3, "3 · Meets expectations"], [2, "2 · Needs improvement"], [1, "1 · Unsatisfactory"]], "review-rating");
    const feedback = textField("Detailed feedback", "review-feedback", "", true);
    form.append(employee, rating, feedback, el("div", "", "form-actions"));
    const actionsRow = form.querySelector(".form-actions");
    actionsRow.append(button("New review", "secondary", () => { state.selectedReview = 0; form.reset(); notify(""); }),
      submitButton(state.selectedReview ? "Update review" : "Save review", "primary"));
    form.addEventListener("submit", saveReview);
    editor.append(form);
    const history = el("section", "", "panel");
    history.append(el("h2", "Review history"), el("p", "Select an employee to filter; edit or remove any review.", "panel-description"));
    const filter = selectField("Filter by employee", [[0, "All employees"], ...state.employees.map(person => [person.id, person.fullName])], "review-filter-employee");
    filter.classList.add("field");
    filter.querySelector("select").value = state.reviewFilter;
    filter.querySelector("select").addEventListener("change", async event => {
      state.reviewFilter = event.target.value;
      await drawReviews();
    });
    const csv = button("Export CSV", "secondary", () => downloadCsv(
      ["Date", "Employee", "Rating", "Feedback"],
      state.reviews.map(review => [review.createdAt, review.employeeName, review.rating, review.feedback]), "apexinsight-reviews.csv"));
    const heading = el("div", "", "section-head");
    const tools = el("div", "", "toolbar");
    tools.append(filter, csv);
    heading.append(el("h2", "Review history", "section-heading"), tools);
    const table = tableSection(["Date", "Employee", "Rating", "Feedback", "Actions"], state.reviews, review => {
      const edit = button("Edit", "secondary tiny", () => {
        state.selectedReview = review.id;
        byId("review-employee").value = String(review.employeeId);
        byId("review-rating").value = String(review.rating);
        byId("review-feedback").value = review.feedback;
        document.querySelector("#review-form .form-actions .primary").textContent = "Update review";
        byId("review-employee").focus();
      });
      const remove = button("Delete", "danger tiny", () => deleteRecord("reviews", review.id, "Delete this review?"));
      return rowWith([review.createdAt.slice(0, 10), review.employeeName, `${review.rating} / 5`, review.feedback,
        actions(edit, remove)]);
    });
    history.replaceChildren(heading, table);
    split.append(editor, history);
    panel.append(split);
  } else {
    const csv = button("Export CSV", "secondary", () => downloadCsv(
      ["Date", "Rating", "Manager", "Feedback"],
      state.reviews.map(review => [review.createdAt, review.rating, review.managerName, review.feedback]),
      "apexinsight-my-reviews.csv"));
    const header = el("div", "", "section-head");
    header.append(el("h2", "Your feedback history", "section-heading"), csv);
    const listing = tableSection(["Date", "Rating", "Manager", "Feedback"], state.reviews, review =>
      rowWith([review.createdAt.slice(0, 10), `${review.rating} / 5`, review.managerName, review.feedback]));
    listing.prepend(header);
    panel.append(listing);
  }
  view.replaceChildren(panel);
}

async function saveReview(event) {
  event.preventDefault();
  const values = { employeeId: Number(byId("review-employee").value), rating: Number(byId("review-rating").value),
    feedback: byId("review-feedback").value };
  try {
    await api("/api/reviews", { method: state.selectedReview ? "PUT" : "POST",
      body: JSON.stringify(state.selectedReview ? { ...values, id: state.selectedReview } : values) });
    state.selectedReview = 0;
    notify("Review saved.");
    await drawReviews();
  } catch (error) { notify(error.message, true); }
}

async function drawGoals() {
  const manager = state.user.role === "MANAGER";
  state.goals = await api("/api/goals");
  const panel = el("section", "", "panel");
  if (manager) {
    const split = el("div", "", "split");
    const editor = el("section", "", "panel");
    editor.append(el("h2", "Assign or update a goal"), el("p", "Set a measurable outcome and a realistic due date.", "panel-description"));
    const form = el("form");
    form.id = "goal-form";
    form.append(
      selectField("Employee", state.employees.map(person => [person.id, `${person.fullName} · ${person.jobTitle}`]), "goal-employee"),
      textField("Goal title", "goal-title"),
      textField("What does success look like?", "goal-description", "", true),
      textField("Due date", "goal-due", new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10)),
      selectField("Status", STATUS_OPTIONS.map(status => [status, status]), "goal-status"),
      textField("Progress (%)", "goal-progress", "0"));
    byId("goal-due").type = "date";
    byId("goal-progress").type = "number";
    byId("goal-progress").min = "0";
    byId("goal-progress").max = "100";
    const footer = el("div", "", "form-actions");
    footer.append(button("New goal", "secondary", () => {
      state.selectedGoal = 0;
      form.reset();
      byId("goal-due").value = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
      byId("goal-progress").value = "0";
      document.querySelector("#goal-form .form-actions .primary").textContent = "Assign goal";
      notify("");
    }),
      submitButton(state.selectedGoal ? "Update goal" : "Assign goal", "primary"));
    form.append(footer);
    form.addEventListener("submit", saveGoal);
    editor.append(form);
    const list = el("section", "", "panel");
    const exportButton = button("Export CSV", "secondary", () => downloadCsv(
      ["Employee", "Goal", "Description", "Due date", "Status", "Progress"],
      state.goals.map(goal => [goal.employeeName, goal.title, goal.description, goal.dueDate, goal.status, `${goal.progress}%`]),
      "apexinsight-goals.csv"));
    const header = el("div", "", "section-head");
    header.append(el("h2", "Team goals", "section-heading"), exportButton);
    const table = tableSection(["Employee", "Goal", "Due date", "Status", "Progress", "Actions"], state.goals, goal => {
      const meter = el("div", "", "progress-readout");
      const progress = document.createElement("progress");
      progress.max = 100;
      progress.value = goal.progress;
      meter.append(progress, el("span", `${goal.progress}%`));
      const edit = button("Edit", "secondary tiny", () => {
        state.selectedGoal = goal.id;
        byId("goal-employee").value = String(goal.employeeId);
        byId("goal-title").value = goal.title;
        byId("goal-description").value = goal.description;
        byId("goal-due").value = goal.dueDate;
        byId("goal-status").value = goal.status;
        byId("goal-progress").value = String(goal.progress);
        document.querySelector("#goal-form .form-actions .primary").textContent = "Update goal";
        byId("goal-title").focus();
      });
      const remove = button("Delete", "danger tiny", () => deleteRecord("goals", goal.id, "Delete this goal?"));
      return rowWith([goal.employeeName, goal.title, goal.dueDate, chip(goal.status), meter, actions(edit, remove)]);
    });
    list.replaceChildren(header, table);
    split.append(editor, list);
    panel.append(split);
  } else {
    const csv = button("Export CSV", "secondary", () => downloadCsv(
      ["Goal", "Description", "Due date", "Status", "Progress"],
      state.goals.map(goal => [goal.title, goal.description, goal.dueDate, goal.status, `${goal.progress}%`]),
      "apexinsight-my-goals.csv"));
    const heading = el("div", "", "section-head");
    heading.append(el("h2", "Your development plan", "section-heading"), csv);
    const table = tableSection(["Goal", "Description", "Due date", "Status", "Your progress", "Save"], state.goals, goal => {
      const input = el("input", "", "progress-input");
      input.type = "number";
      input.min = "0";
      input.max = "100";
      input.step = "5";
      input.value = String(goal.progress);
      input.setAttribute("aria-label", `Update progress on ${goal.title} (0 to 100 percent)`);
      const save = button("Update", "primary tiny", async () => {
        try {
          await api("/api/goals/progress", { method: "PUT",
            body: JSON.stringify({ id: goal.id, progress: Number(input.value) }) });
          notify("Goal progress updated.");
          await drawGoals();
        } catch (error) { notify(error.message, true); }
      });
      return rowWith([goal.title, goal.description, goal.dueDate, chip(goal.status), input, save]);
    });
    table.prepend(heading);
    panel.append(table);
  }
  view.replaceChildren(panel);
}

async function saveGoal(event) {
  event.preventDefault();
  const goal = { employeeId: Number(byId("goal-employee").value), title: byId("goal-title").value,
    description: byId("goal-description").value, dueDate: byId("goal-due").value,
    status: byId("goal-status").value, progress: Number(byId("goal-progress").value) };
  try {
    await api("/api/goals", { method: state.selectedGoal ? "PUT" : "POST",
      body: JSON.stringify(state.selectedGoal ? { ...goal, id: state.selectedGoal } : goal) });
    state.selectedGoal = 0;
    notify("Goal saved.");
    await drawGoals();
  } catch (error) { notify(error.message, true); }
}

async function drawTeam() {
  state.employees = await api("/api/employees");
  const split = el("div", "", "split");
  const panel = el("section", "", "panel");
  panel.append(el("h2", "Employee accounts"), el("p", "Create profiles, assign job titles, and reset access.", "panel-description"));
  const form = el("form");
  form.id = "employee-form";
  form.append(textField("Username", "person-username"), textField("Full name", "person-name"),
    textField("Department", "person-department"), textField("Job title", "person-title"),
    textField("Password (new accounts only)", "person-password"));
  byId("person-password").type = "password";
  byId("person-password").autocomplete = "new-password";
  const footer = el("div", "", "form-actions");
  footer.append(button("New employee", "secondary", () => { state.selectedEmployee = 0; form.reset(); notify(""); }),
    submitButton(state.selectedEmployee ? "Update employee" : "Create employee", "primary"));
  form.append(footer);
  form.addEventListener("submit", saveEmployee);
  panel.append(form);
  const list = el("section", "", "panel");
  const head = el("div", "", "section-head");
  head.append(el("h2", "Your team", "section-heading"), button("Refresh", "secondary", () => openPage("team")));
  const table = tableSection(["Name", "Username", "Department", "Job title", "Actions"], state.employees, employee => {
    const edit = button("Edit", "secondary tiny", () => {
      state.selectedEmployee = employee.id;
      byId("person-username").value = employee.username;
      byId("person-name").value = employee.fullName;
      byId("person-department").value = employee.department;
      byId("person-title").value = employee.jobTitle;
      byId("person-password").value = "";
      document.querySelector("#employee-form .form-actions .primary").textContent = "Update employee";
    });
    const reset = button("Reset password", "secondary tiny", async () => {
      const secret = prompt(`Set a new password for ${employee.fullName} (8–128 characters):`);
      if (secret === null) return;
      try {
        await api("/api/employees/password", { method: "PUT",
          body: JSON.stringify({ id: employee.id, password: secret }) });
        notify("The employee password was reset.");
      } catch (error) { notify(error.message, true); }
    });
    const remove = button("Delete", "danger tiny", () =>
      deleteRecord("employees", employee.id, `Delete ${employee.fullName} and their reviews and goals?`));
    return rowWith([employee.fullName, employee.username, employee.department,
      employee.jobTitle, actions(edit, reset, remove)]);
  });
  list.replaceChildren(head, table);
  split.append(panel, list);
  view.replaceChildren(split);
}

async function saveEmployee(event) {
  event.preventDefault();
  const employee = { username: byId("person-username").value, fullName: byId("person-name").value,
    department: byId("person-department").value, jobTitle: byId("person-title").value };
  const password = byId("person-password").value;
  if (!state.selectedEmployee && !password) { notify("Enter an initial password for the new employee.", true); return; }
  try {
    await api("/api/employees", { method: state.selectedEmployee ? "PUT" : "POST",
      body: JSON.stringify(state.selectedEmployee
        ? { ...employee, id: state.selectedEmployee }
        : { ...employee, password }) });
    state.selectedEmployee = 0;
    notify("Employee profile saved.");
    await drawTeam();
  } catch (error) { notify(error.message, true); }
}

function drawAccount() {
  const panel = el("section", "", "panel");
  panel.append(el("h2", "Update your password"), el("p", "Your password is verified before it is changed and stored as a salted hash.", "panel-description"));
  const form = el("form");
  form.id = "account-form";
  form.append(textField("Current password", "account-current"), textField("New password (8–128 characters)", "account-new"),
    textField("Confirm new password", "account-confirm"));
  for (const id of ["account-current", "account-new", "account-confirm"]) {
    byId(id).type = "password";
    byId(id).autocomplete = id === "account-current" ? "current-password" : "new-password";
  }
  const footer = el("div", "", "form-actions");
  footer.append(submitButton("Change password", "primary"));
  form.append(footer);
  form.addEventListener("submit", async event => {
    event.preventDefault();
    const currentPassword = byId("account-current").value;
    const newPassword = byId("account-new").value;
    if (newPassword !== byId("account-confirm").value) { notify("The new passwords do not match.", true); return; }
    try {
      await api("/api/password", { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) });
      form.reset();
      notify("Your password was changed.");
    } catch (error) { notify(error.message, true); }
  });
  panel.append(form);
  view.replaceChildren(panel);
}

function textField(label, id, value = "", wide = false) {
  const field = el("div", "", `field${wide ? " wide" : ""}`);
  const caption = el("label", label);
  caption.htmlFor = id;
  const control = wide ? document.createElement("textarea") : document.createElement("input");
  control.id = id;
  control.required = true;
  if (value) control.value = value;
  field.append(caption, control);
  return field;
}

function selectField(label, options, id) {
  const field = el("div", "", "field");
  const caption = el("label", label);
  caption.htmlFor = id;
  const select = document.createElement("select");
  select.id = id;
  select.required = true;
  for (const [value, text] of options) {
    const option = el("option", text);
    option.value = String(value);
    select.append(option);
  }
  field.append(caption, select);
  return field;
}

function submitButton(label, variant) {
  const submit = button(label, variant, () => {});
  submit.type = "submit";
  return submit;
}

function chip(status) {
  const value = el("span", status, `chip${status === "Completed" ? " success" : status === "At risk" ? " warning" : ""}`);
  return value;
}

async function deleteRecord(kind, id, message) {
  if (!confirm(message)) return;
  try {
    await api(`/api/${kind}?id=${encodeURIComponent(id)}`, { method: "DELETE" });
    state.selectedReview = 0;
    state.selectedGoal = 0;
    state.selectedEmployee = 0;
    notify("Record deleted.");
    await openPage(state.page);
  } catch (error) { notify(error.message, true); }
}

function downloadCsv(columns, rows, filename) {
  const quote = value => `"${String(value ?? "").replaceAll('"', '""')}"`;
  const content = [columns, ...rows].map(row => row.map(quote).join(",")).join("\r\n");
  const link = document.createElement("a");
  link.href = URL.createObjectURL(new Blob(["\ufeff", content], { type: "text/csv;charset=utf-8" }));
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

byId("login-form").addEventListener("submit", async event => {
  event.preventDefault();
  const status = byId("login-message");
  status.textContent = "Signing in…";
  try {
    state.user = await api("/api/login", { method: "POST",
      body: JSON.stringify({ username: byId("login-username").value, password: byId("login-password").value }) });
    byId("login-password").value = "";
    showApp();
  } catch (error) { status.textContent = error.message; }
});

document.querySelectorAll(".account-choice").forEach(choice => {
  choice.addEventListener("click", () => {
    byId("login-username").value = choice.dataset.user;
    byId("login-password").value = choice.dataset.pass;
    byId("login-form").requestSubmit();
  });
});

byId("logout").addEventListener("click", async () => {
  try { await api("/api/logout", { method: "POST", body: "{}" }); }
  finally { state.user = null; showLogin(); }
});

byId("refresh").addEventListener("click", () => openPage(state.page));
byId("change-password").addEventListener("click", () => byId("password-dialog").showModal());
byId("cancel-password").addEventListener("click", () => byId("password-dialog").close());
byId("password-form").addEventListener("submit", async event => {
  event.preventDefault();
  const currentPassword = byId("current-password").value;
  const newPassword = byId("new-password").value;
  if (newPassword !== byId("confirm-password").value) { alert("The new passwords do not match."); return; }
  try {
    await api("/api/password", { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) });
    byId("password-dialog").close();
    notify("Your password was changed.");
  } catch (error) { notify(error.message, true); }
});

api("/api/me").then(user => { state.user = user; showApp(); })
  .catch(() => showLogin());
