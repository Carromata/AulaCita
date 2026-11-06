const api = "/api/v1";
const notice = document.getElementById("notice");

function csrfToken() {
    const cookie = document.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="));
    return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : "";
}

async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    if (options.body && !(options.body instanceof FormData)) {
        headers.set("Content-Type", "application/json");
    }
    if (options.method && options.method !== "GET") {
        headers.set("X-XSRF-TOKEN", csrfToken());
    }
    const response = await fetch(`${api}${path}`, {...options, headers});
    const payload = response.status === 204 ? null : await response.json();
    if (!response.ok) {
        throw new Error(payload?.message || `No se pudo completar la solicitud (${response.status}).`);
    }
    return payload?.data;
}

function showNotice(message, isError = false) {
    notice.textContent = message;
    notice.className = `alert ${isError ? "alert-error" : "alert-success"}`;
    notice.hidden = false;
}

function clear(element) {
    while (element.firstChild) element.removeChild(element.firstChild);
}

function addOption(select, value, text) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = text;
    select.append(option);
}

function emptyRow(body, columns, message) {
    const row = body.insertRow();
    const cell = row.insertCell();
    cell.colSpan = columns;
    cell.textContent = message;
}

async function loadRole() {
    const me = await request("/auth/me");
    const roles = me.roles || [];
    const isSuper = roles.includes("ROLE_SUPERADMINISTRADOR");
    const isAdmin = roles.includes("ROLE_ADMINISTRADOR");
    const canManageUsers = isSuper || isAdmin;
    const canManageRelations = canManageUsers || roles.includes("ROLE_SECRETARIA");

    document.getElementById("users-section").hidden = !canManageUsers;
    document.getElementById("relations-section").hidden = !canManageRelations;
    document.getElementById("broadcast-section").hidden = !isSuper;
    document.getElementById("images-section").hidden = !isSuper;

    document.getElementById("scope-description").textContent = isSuper
        ? "Como super administrador puedes gestionar administradores, usuarios, estudiantes, relaciones, imágenes institucionales y avisos generales."
        : isAdmin
            ? "Como administrador puedes gestionar docentes, apoderados, estudiantes y sus relaciones. Las cuentas administrativas, imágenes institucionales y avisos generales son exclusivos del super administrador."
            : "Como secretaría puedes gestionar estudiantes y relaciones de apoderados. Los usuarios y las imágenes institucionales son exclusivos de administración.";

    document.getElementById("users-hint").textContent = isSuper
        ? "Puedes crear y editar administradores, secretaría, dirección, docentes y apoderados."
        : "Puedes crear y editar docentes y apoderados. Las cuentas administrativas y de secretaría solo las gestiona el super administrador.";

    const roleSelect = document.getElementById("user-role");
    clear(roleSelect);
    if (isSuper) {
        addOption(roleSelect, "Administrador", "Administrador");
        addOption(roleSelect, "Secretaria", "Secretaría");
        addOption(roleSelect, "Director", "Dirección");
    }
    addOption(roleSelect, "Docente", "Docente");
    addOption(roleSelect, "Apoderado", "Apoderado");

    return {isSuper, isAdmin, canManageUsers, canManageRelations};
}

async function loadGrades() {
    const grades = await request("/admin/grados");
    const select = document.getElementById("student-grade");
    clear(select);
    for (const grade of grades) addOption(select, grade.id, grade.label);
}

async function loadStudents() {
    const body = document.getElementById("students-body");
    clear(body);
    const students = await request("/admin/estudiantes");
    document.getElementById("management-students-count").textContent = String(students.length);
    if (!students.length) {
        emptyRow(body, 5, "No hay estudiantes.");
        return;
    }
    for (const student of students) {
        const row = body.insertRow();
        row.insertCell().textContent = student.dni || "—";
        row.insertCell().textContent = `${student.firstName} ${student.lastName}${student.motherLastName ? " " + student.motherLastName : ""}`;
        row.insertCell().textContent = student.gradeLabel;
        row.insertCell().textContent = student.active ? "Sí" : "No";
        const actions = row.insertCell();
        const button = document.createElement("button");
        button.type = "button";
        button.className = "button button-outline button-small";
        button.textContent = "Editar";
        button.addEventListener("click", () => fillStudent(student));
        actions.append(button);
    }
}

function fillStudent(student) {
    document.getElementById("student-id").value = student.id;
    document.getElementById("student-dni").value = student.dni || "";
    document.getElementById("student-code").value = student.code || "";
    document.getElementById("student-first").value = student.firstName || "";
    document.getElementById("student-last").value = student.lastName || "";
    document.getElementById("student-mother").value = student.motherLastName || "";
    document.getElementById("student-grade").value = String(student.gradeSectionId);
    document.getElementById("student-active").value = String(student.active);
    document.getElementById("student-first").focus();
}

function resetStudentForm() {
    document.getElementById("student-form").reset();
    document.getElementById("student-id").value = "";
    document.getElementById("student-active").value = "true";
}

document.getElementById("student-reset").addEventListener("click", resetStudentForm);

document.getElementById("student-form").addEventListener("submit", async event => {
    event.preventDefault();
    const id = document.getElementById("student-id").value;
    const payload = {
        dni: document.getElementById("student-dni").value.trim(),
        code: document.getElementById("student-code").value.trim() || null,
        firstName: document.getElementById("student-first").value.trim(),
        lastName: document.getElementById("student-last").value.trim(),
        motherLastName: document.getElementById("student-mother").value.trim() || null,
        gradeSectionId: Number(document.getElementById("student-grade").value),
        active: document.getElementById("student-active").value === "true"
    };
    const button = event.currentTarget.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        if (id) {
            await request(`/admin/estudiantes/${id}`, {method: "PUT", body: JSON.stringify(payload)});
            showNotice("Estudiante actualizado.");
        } else {
            await request("/admin/estudiantes", {method: "POST", body: JSON.stringify(payload)});
            showNotice("Estudiante creado.");
        }
        resetStudentForm();
        await loadStudents();
        await loadRelationOptions();
    } catch (error) {
        showNotice(error.message, true);
    } finally {
        button.disabled = false;
    }
});

/* Usuarios ------------------------------------------------------------- */
async function loadUsers(isSuper) {
    const body = document.getElementById("users-body");
    const section = document.getElementById("users-section");
    if (!body || section.hidden) return;
    clear(body);
    const users = await request("/admin/usuarios");
    document.getElementById("management-teachers-count").textContent =
        String(users.filter(user => user.role === "Docente" && user.active).length);
    document.getElementById("management-guardians-count").textContent =
        String(users.filter(user => user.role === "Apoderado" && user.active).length);
    if (!users.length) {
        emptyRow(body, 5, "No hay usuarios para mostrar.");
        return;
    }
    for (const user of users) {
        const row = body.insertRow();
        row.insertCell().textContent = user.email;
        row.insertCell().textContent = user.fullName;
        const roleCell = row.insertCell();
        const rolePill = document.createElement("span");
        rolePill.className = "role-pill";
        rolePill.textContent = user.role;
        roleCell.append(rolePill);
        row.insertCell().textContent = user.active ? "Sí" : "No";
        const actions = row.insertCell();
        const edit = document.createElement("button");
        edit.type = "button";
        edit.className = "table-action";
        edit.textContent = "Editar";
        edit.addEventListener("click", () => fillUser(user));
        const reset = document.createElement("button");
        reset.type = "button";
        reset.className = "table-action";
        reset.textContent = "Cambiar clave";
        reset.addEventListener("click", () => resetUserPassword(user));
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "table-action";
        toggle.textContent = user.active ? "Desactivar" : "Activar";
        toggle.addEventListener("click", () => toggleUser(user));
        actions.append(edit, reset, toggle);
    }
}

function fillUser(user) {
    document.getElementById("user-id").value = user.id;
    document.getElementById("user-email").value = user.email;
    document.getElementById("user-email").readOnly = true;
    document.getElementById("password-field").hidden = true;
    document.getElementById("user-password").required = false;
    document.getElementById("user-first").value = user.firstName || user.fullName?.split(" ")[0] || "";
    document.getElementById("user-last").value = user.lastName || "";
    document.getElementById("user-mother").value = user.motherLastName || "";
    document.getElementById("user-role").value = user.role;
    document.getElementById("user-active-field").hidden = false;
    document.getElementById("user-active").value = String(user.active);
    document.getElementById("user-phone").value = user.phone || "";
    document.getElementById("user-document-type").value = user.documentType || "";
    document.getElementById("user-document-number").value = user.documentNumber || "";
    document.getElementById("user-submit").textContent = "Guardar cambios";
    document.getElementById("user-first").focus();
}

function resetUserForm() {
    document.getElementById("user-form").reset();
    document.getElementById("user-id").value = "";
    document.getElementById("user-email").readOnly = false;
    document.getElementById("password-field").hidden = false;
    document.getElementById("user-password").required = true;
    document.getElementById("user-active-field").hidden = true;
    document.getElementById("user-active").value = "true";
    document.getElementById("user-phone").value = "";
    document.getElementById("user-document-type").value = "";
    document.getElementById("user-document-number").value = "";
    document.getElementById("user-submit").textContent = "Crear usuario";
}

document.getElementById("user-reset").addEventListener("click", resetUserForm);

document.getElementById("user-form").addEventListener("submit", async event => {
    event.preventDefault();
    const id = document.getElementById("user-id").value;
    const button = document.getElementById("user-submit");
    button.disabled = true;
    try {
        if (id) {
            const payload = {
                firstName: document.getElementById("user-first").value.trim(),
                lastName: document.getElementById("user-last").value.trim(),
                motherLastName: document.getElementById("user-mother").value.trim() || null,
                role: document.getElementById("user-role").value,
                active: document.getElementById("user-active").value === "true",
                phone: document.getElementById("user-phone").value.trim() || null,
                documentType: document.getElementById("user-document-type").value || null,
                documentNumber: document.getElementById("user-document-number").value.trim() || null
            };
            await request(`/admin/usuarios/${id}`, {method: "PUT", body: JSON.stringify(payload)});
            showNotice("Usuario actualizado.");
        } else {
            const payload = {
                email: document.getElementById("user-email").value.trim(),
                password: document.getElementById("user-password").value,
                firstName: document.getElementById("user-first").value.trim(),
                lastName: document.getElementById("user-last").value.trim(),
                motherLastName: document.getElementById("user-mother").value.trim() || null,
                role: document.getElementById("user-role").value,
                phone: document.getElementById("user-phone").value.trim() || null,
                documentType: document.getElementById("user-document-type").value || null,
                documentNumber: document.getElementById("user-document-number").value.trim() || null
            };
            await request("/admin/usuarios", {method: "POST", body: JSON.stringify(payload)});
            showNotice("Usuario creado.");
        }
        resetUserForm();
        await loadUsers(true);
    } catch (error) {
        showNotice(error.message, true);
    } finally {
        button.disabled = false;
    }
});

async function resetUserPassword(user) {
    const password = window.prompt(`Nueva contraseña para ${user.email} (mínimo 12 caracteres):`);
    if (!password) return;
    if (password.length < 12) {
        showNotice("La contraseña debe tener al menos 12 caracteres.", true);
        return;
    }
    try {
        await request(`/admin/usuarios/${user.id}/password`, {
            method: "POST",
            body: JSON.stringify({password})
        });
        showNotice(`Contraseña actualizada para ${user.email}.`);
    } catch (error) {
        showNotice(error.message, true);
    }
}

async function toggleUser(user) {
    if (!window.confirm(`¿${user.active ? "Desactivar" : "Activar"} la cuenta ${user.email}?`)) return;
    try {
        await request(`/admin/usuarios/${user.id}/activo?activo=${!user.active}`, {method: "POST"});
        showNotice(user.active ? "Usuario desactivado." : "Usuario activado.");
        await loadUsers(true);
    } catch (error) {
        showNotice(error.message, true);
    }
}

/* Relaciones ----------------------------------------------------------- */
async function loadRelationOptions() {
    const section = document.getElementById("relations-section");
    if (section.hidden) return;
    const studentSelect = document.getElementById("relation-student");
    const guardianSelect = document.getElementById("relation-guardian");
    const [students, guardians, teachers] = await Promise.all([
        request("/admin/estudiantes"),
        request("/admin/apoderados"),
        request("/docentes")
    ]);
    document.getElementById("management-guardians-count").textContent = String(guardians.length);
    document.getElementById("management-teachers-count").textContent = String(teachers.length);
    clear(studentSelect);
    clear(guardianSelect);
    for (const student of students) {
        addOption(studentSelect, student.id,
            `${student.firstName} ${student.lastName} · ${student.gradeLabel}`);
    }
    for (const guardian of guardians) addOption(guardianSelect, guardian.id, guardian.name);
    if (!guardians.length) addOption(guardianSelect, "", "Primero crea un usuario apoderado");
}

async function loadRelations() {
    const body = document.getElementById("relations-body");
    const section = document.getElementById("relations-section");
    if (section.hidden) return;
    clear(body);
    const relations = await request("/admin/relaciones");
    document.getElementById("management-relations-count").textContent = String(relations.length);
    if (!relations.length) {
        emptyRow(body, 6, "Aún no hay relaciones registradas.");
        return;
    }
    for (const relation of relations) {
        const row = body.insertRow();
        row.insertCell().textContent = relation.student;
        row.insertCell().textContent = relation.guardian;
        row.insertCell().textContent = relation.relationship;
        row.insertCell().textContent = relation.canBook ? "Sí" : "No";
        row.insertCell().textContent = relation.active ? "Sí" : "No";
        const actions = row.insertCell();
        const edit = document.createElement("button");
        edit.type = "button";
        edit.className = "table-action";
        edit.textContent = "Editar";
        edit.addEventListener("click", () => fillRelation(relation));
        actions.append(edit);
    }
}

function fillRelation(relation) {
    document.getElementById("relation-id").value = relation.id;
    document.getElementById("relation-student").value = String(relation.studentId);
    document.getElementById("relation-guardian").value = String(relation.guardianId);
    document.getElementById("relation-relationship").value = relation.relationship || "";
    document.getElementById("relation-can-book").value = String(relation.canBook);
}

function resetRelationForm() {
    document.getElementById("relation-form").reset();
    document.getElementById("relation-id").value = "";
    document.getElementById("relation-can-book").value = "true";
}

document.getElementById("relation-reset").addEventListener("click", resetRelationForm);

document.getElementById("relation-form").addEventListener("submit", async event => {
    event.preventDefault();
    const id = document.getElementById("relation-id").value;
    const payload = {
        studentId: Number(document.getElementById("relation-student").value),
        guardianId: Number(document.getElementById("relation-guardian").value),
        relationship: document.getElementById("relation-relationship").value.trim(),
        canBook: document.getElementById("relation-can-book").value === "true"
    };
    const button = event.currentTarget.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        if (id) {
            await request(`/admin/relaciones/${id}`, {method: "PUT", body: JSON.stringify(payload)});
            showNotice("Relación actualizada.");
        } else {
            await request("/admin/relaciones", {method: "POST", body: JSON.stringify(payload)});
            showNotice("Relación creada. El apoderado ya puede reservar si está habilitado.");
        }
        resetRelationForm();
        await loadRelations();
    } catch (error) {
        showNotice(error.message, true);
    } finally {
        button.disabled = false;
    }
});

/* Avisos generales ----------------------------------------------------- */
const broadcastForm = document.getElementById("broadcast-form");
if (broadcastForm) {
    broadcastForm.addEventListener("submit", async event => {
        event.preventDefault();
        const button = event.currentTarget.querySelector("button[type=submit]");
        button.disabled = true;
        try {
            const result = await request("/admin/notificaciones", {
                method: "POST",
                body: JSON.stringify({
                    title: document.getElementById("broadcast-title").value.trim(),
                    message: document.getElementById("broadcast-message").value.trim(),
                    role: document.getElementById("broadcast-role").value
                })
            });
            showNotice(result?.message || "Aviso enviado.");
            event.currentTarget.reset();
        } catch (error) {
            showNotice(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
}

/* Imágenes institucionales -------------------------------------------- */
async function loadImages() {
    const preview = document.getElementById("images-preview");
    if (!preview || document.getElementById("images-section").hidden) return;
    clear(preview);
    const images = await request("/admin/imagenes");
    if (!images.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Aún no hay imágenes cargadas.";
        preview.append(empty);
        return;
    }
    for (const image of images) {
        const card = document.createElement("article");
        card.className = "metric-card";
        const title = document.createElement("strong");
        title.textContent = image.key;
        const img = document.createElement("img");
        img.src = `${image.url}?t=${Date.now()}`;
        img.alt = image.key;
        img.style.maxWidth = "100%";
        img.style.borderRadius = ".5rem";
        img.style.marginTop = ".6rem";
        const caption = document.createElement("span");
        caption.className = "metric-caption";
        caption.textContent = image.originalName || "";
        card.append(title, img, caption);
        preview.append(card);
    }
}

document.getElementById("image-form").addEventListener("submit", async event => {
    event.preventDefault();
    const key = document.getElementById("image-key").value;
    const file = document.getElementById("image-file").files[0];
    const body = new FormData();
    body.append("archivo", file);
    const button = event.currentTarget.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        await request(`/admin/imagenes/${encodeURIComponent(key)}`, {method: "POST", body});
        showNotice("Imagen actualizada.");
        document.getElementById("image-file").value = "";
        await loadImages();
    } catch (error) {
        showNotice(error.message, true);
    } finally {
        button.disabled = false;
    }
});

(async function init() {
    try {
        const role = await loadRole();
        await loadGrades();
        await loadStudents();
        if (role.canManageUsers) await loadUsers(role.isSuper);
        if (role.canManageRelations) {
            await loadRelationOptions();
            await loadRelations();
        }
        if (role.isSuper) await loadImages();
    } catch (error) {
        showNotice(error.message, true);
    }
})();
