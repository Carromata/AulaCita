const api = "/api/v1";
const notice = document.getElementById("notice");
const roleNames = {
    ROLE_ADMINISTRADOR: "ADMINISTRADOR",
    ROLE_SUPERADMINISTRADOR: "SUPERADMINISTRADOR",
    ROLE_SECRETARIA: "SECRETARIA",
    ROLE_DIRECTOR: "DIRECTOR",
    ROLE_DOCENTE: "DOCENTE",
    ROLE_APODERADO: "APODERADO"
};
const dayNames = ["", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
const imageModal = document.getElementById("image-modal");
let identity = {};
let currentAppointment = null;
let appointmentItems = [];
let currentWeekStart = null;
let availabilityTeachers = {};

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

function statusClass(status) {
    return status.toLocaleLowerCase("es").replaceAll(" ", "-");
}

function addCell(row, text) {
    const cell = document.createElement("td");
    cell.textContent = text || "";
    row.append(cell);
    return cell;
}

function isStaff(role) {
    return ["ADMINISTRADOR", "SUPERADMINISTRADOR", "SECRETARIA"].includes(role);
}

function formatAppointmentDate(value) {
    return new Intl.DateTimeFormat("es-PE", {
        weekday: "short", day: "numeric", month: "short", timeZone: "UTC"
    }).format(new Date(`${value}T12:00:00Z`)).replaceAll(".", "");
}

function filterAppointments() {
    const query = document.getElementById("appointment-search").value.trim().toLocaleLowerCase("es");
    const status = document.getElementById("appointment-filter").value;
    const cards = document.querySelectorAll(".appointment-card");
    let visible = 0;
    for (const card of cards) {
        const matchesText = card.dataset.search.includes(query);
        const matchesStatus = status === "TODAS" || card.dataset.status === status;
        card.hidden = !matchesText || !matchesStatus;
        if (!card.hidden) visible++;
    }
    const empty = document.getElementById("appointment-filter-empty");
    if (empty) empty.hidden = visible !== 0;
    renderWeekCalendar();
}

async function loadAppointments(role) {
    const body = document.getElementById("appointments");
    clear(body);
    try {
        const items = [];
        let reachedLimit = true;
        for (let page = 0; page < 50; page++) {
            const batch = await request(`/citas?pagina=${page}&limite=100`);
            items.push(...batch);
            if (batch.length < 100) {
                reachedLimit = false;
                break;
            }
        }
        appointmentItems = items;
        if (reachedLimit) showNotice("La agenda alcanzó el límite de 5,000 citas por carga. Refina los filtros o contacta al administrador.", true);
        document.getElementById("appointment-total").textContent = String(items.length);
        document.getElementById("appointment-pending").textContent =
            String(items.filter(item => item.status === "Pendiente").length);
        document.getElementById("appointment-confirmed").textContent =
            String(items.filter(item => item.status === "Confirmada").length);
        const upcoming = items
            .filter(item => item.date >= new Intl.DateTimeFormat("en-CA", {timeZone: "America/Lima"}).format(new Date())
                && ["Pendiente", "Confirmada"].includes(item.status))
            .sort((left, right) => `${left.date}T${left.startTime}`.localeCompare(`${right.date}T${right.startTime}`))[0];
        document.getElementById("appointment-next").textContent = upcoming ? formatAppointmentDate(upcoming.date) : "—";
        if (!currentWeekStart) currentWeekStart = startOfWeek(limaToday());
        renderWeekCalendar();
        if (!items.length) {
            const empty = document.createElement("p");
            empty.className = "muted empty-state";
            empty.textContent = role === "APODERADO"
                ? "Todavía no hay citas para tus estudiantes vinculados."
                : role === "DOCENTE" ? "Tu agenda todavía no tiene entrevistas." : "No hay citas registradas para mostrar.";
            body.append(empty);
            return;
        }
        const sortedItems = [...items].sort((left, right) =>
            `${right.date}T${right.startTime}`.localeCompare(`${left.date}T${left.startTime}`));
        for (const item of sortedItems) {
            const card = document.createElement("article");
            card.className = "appointment-card";
            card.dataset.status = item.status;
            card.dataset.search = `${item.code} ${item.student} ${item.guardian} ${item.teacher} ${item.reason}`.toLocaleLowerCase("es");

            const dateBlock = document.createElement("div");
            dateBlock.className = "appointment-date-block";
            const date = document.createElement("strong");
            date.textContent = formatAppointmentDate(item.date);
            const time = document.createElement("span");
            time.textContent = `${item.startTime}–${item.endTime}`;
            dateBlock.append(date, time);

            const content = document.createElement("div");
            content.className = "appointment-card-content";
            const heading = document.createElement("div");
            heading.className = "appointment-card-heading";
            const title = document.createElement("h3");
            title.textContent = item.student;
            const badge = document.createElement("span");
            badge.className = `status-badge status-${statusClass(item.status)}`;
            badge.textContent = item.status;
            heading.append(title, badge);
            const people = document.createElement("p");
            people.className = "appointment-card-people";
            people.textContent = role === "APODERADO"
                ? `Docente · ${item.teacher}`
                : role === "DOCENTE"
                    ? `Apoderado · ${item.guardian}`
                    : `${item.teacher} · ${item.guardian}`;
            const reason = document.createElement("p");
            reason.className = "appointment-card-reason";
            reason.textContent = item.reason;
            const meta = document.createElement("div");
            meta.className = "appointment-card-meta";
            const code = document.createElement("span");
            code.textContent = item.code;
            const modality = document.createElement("span");
            modality.textContent = item.modality === "VIRTUAL" ? "Videollamada" : "Presencial";
            meta.append(code, modality);
            content.append(heading, people, reason, meta);

            const actions = document.createElement("div");
            actions.className = "appointment-card-actions";
            const active = ["Pendiente", "Confirmada"].includes(item.status);

            addAction(actions, "Descargar calendario", () => downloadCalendarEvent(item));

            if (["Pendiente"].includes(item.status) && (isStaff(role) || role === "DOCENTE")) {
                addAction(actions, "Confirmar", async () => {
                    await request(`/citas/${item.id}/confirmar`, {
                        method: "POST",
                        headers: {"If-Match": item.version}
                    });
                    showNotice("La cita quedó confirmada.");
                    await loadAppointments(role);
                });
            }
            if (role === "DOCENTE" && item.status === "Confirmada") {
                addAction(actions, "Registrar atención", async () => {
                    const attended = window.confirm("¿El apoderado asistió a la reunión?");
                    await request(`/citas/${item.id}/asistencia`, {
                        method: "POST",
                        body: JSON.stringify({attended, observation: "", version: item.version})
                    });
                    showNotice("Se registró el resultado de la cita.");
                    await loadAppointments(role);
                });
            }
            if ((role === "APODERADO" || role === "DOCENTE" || isStaff(role)) && active) {
                addAction(actions, "Reprogramar", async () => {
                    const date = window.prompt("Nueva fecha (AAAA-MM-DD):");
                    if (!date) return;
                    const slots = await request(`/disponibilidades?docenteId=${item.teacherId}&fecha=${encodeURIComponent(date)}&relacionId=${item.studentGuardianRelationId}`);
                    if (!slots.length) {
                        showNotice("No hay horarios disponibles para esa fecha.", true);
                        return;
                    }
                    const choices = slots.map((slot, index) => `${index + 1}. ${slot.startTime}–${slot.endTime}`).join("\n");
                    const selected = Number(window.prompt(`Elige un horario:\n${choices}`));
                    if (!Number.isInteger(selected) || selected < 1 || selected > slots.length) return;
                    const slot = slots[selected - 1];
                    await request(`/citas/${item.id}/reprogramar`, {
                        method: "POST",
                        body: JSON.stringify({
                            version: item.version,
                            replacement: {
                                studentGuardianRelationId: item.studentGuardianRelationId,
                                teacherId: item.teacherId,
                                date,
                                startTime: slot.startTime,
                                modality: item.modality,
                                reason: item.reason,
                                comment: ""
                            }
                        })
                    });
                    showNotice("La cita fue reprogramada. El historial conserva la cita anterior.");
                    await loadAppointments(role);
                });
                addAction(actions, "Cancelar", async () => {
                    if (!window.confirm(`¿Cancelar la cita ${item.code}?`)) return;
                    const reason = window.prompt("Motivo de cancelación:");
                    if (!reason?.trim()) return;
                    await request(`/citas/${item.id}/cancelar`, {
                        method: "POST",
                        body: JSON.stringify({reason: reason.trim(), version: item.version})
                    });
                    showNotice("La cita quedó cancelada y el horario se liberó.");
                    await loadAppointments(role);
                });
            }
            addAction(actions, "Imágenes", () => openImages(item));
            addAction(actions, "Historial", async () => {
                const history = await request(`/citas/${item.id}/historial`);
                const summary = history.map(entry =>
                    `${entry.occurredAt}: ${entry.event} (${entry.previousStatus || "—"} → ${entry.newStatus || "—"})`
                ).join("\n");
                window.alert(summary || "Esta cita aún no tiene movimientos registrados.");
            });
            card.append(dateBlock, content, actions);
            body.append(card);
        }
        const filteredEmpty = document.createElement("p");
        filteredEmpty.id = "appointment-filter-empty";
        filteredEmpty.className = "muted empty-state";
        filteredEmpty.hidden = true;
        filteredEmpty.textContent = "No hay citas que coincidan con esta búsqueda o estado.";
        body.append(filteredEmpty);
        filterAppointments();
    } catch (error) {
        showNotice(error.message, true);
        const empty = document.createElement("p");
        empty.className = "muted empty-state";
        empty.textContent = "No fue posible cargar las citas.";
        body.append(empty);
    }
}

function addAction(parent, label, action) {
    const button = document.createElement("button");
    button.className = "table-action";
    button.type = "button";
    button.textContent = label;
    button.addEventListener("click", async () => {
        button.disabled = true;
        try {
            await action();
        } catch (error) {
            showNotice(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
    parent.append(button);
}

function downloadCalendarEvent(item) {
    const escapeText = value => String(value || "")
        .replaceAll("\\", "\\\\")
        .replace(/\r\n|\r|\n/g, "\\n")
        .replaceAll(",", "\\,")
        .replaceAll(";", "\\;");
    const utcStamp = (time, date = item.date) =>
        new Date(`${date}T${time}-05:00`).toISOString().replaceAll("-", "").replaceAll(":", "").replace(/\.\d{3}Z$/, "Z");
    const description = [
        `Estudiante: ${item.student}`,
        `Docente: ${item.teacher}`,
        `Apoderado: ${item.guardian}`,
        `Motivo: ${item.reason}`,
        `Modalidad: ${item.modality === "VIRTUAL" ? "Virtual" : "Presencial"}`,
        `Estado: ${item.status}`
    ].map(escapeText).join("\\n");
    const event = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//AulaCita//Agenda Escolar//ES",
        "CALSCALE:GREGORIAN",
        "BEGIN:VEVENT",
        `UID:${escapeText(item.code)}@aulacita`,
        `DTSTAMP:${new Date().toISOString().replaceAll("-", "").replaceAll(":", "").replace(/\.\d{3}Z$/, "Z")}`,
        `DTSTART:${utcStamp(item.startTime)}`,
        `DTEND:${utcStamp(item.endTime)}`,
        `SUMMARY:${escapeText(`AulaCita · ${item.student} · ${item.teacher}`)}`,
        `DESCRIPTION:${description}`,
        "END:VEVENT",
        "END:VCALENDAR",
        ""
    ].join("\r\n");
    const blob = new Blob([event], {type: "text/calendar;charset=utf-8"});
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${item.code}.ics`;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function limaToday() {
    return new Intl.DateTimeFormat("en-CA", {timeZone: "America/Lima"}).format(new Date());
}

function startOfWeek(dateString) {
    const date = new Date(`${dateString}T12:00:00Z`);
    const day = date.getUTCDay() || 7;
    date.setUTCDate(date.getUTCDate() - day + 1);
    return date.toISOString().slice(0, 10);
}

function shiftDate(dateString, days) {
    const date = new Date(`${dateString}T12:00:00Z`);
    date.setUTCDate(date.getUTCDate() + days);
    return date.toISOString().slice(0, 10);
}

function calendarDateLabel(dateString, options) {
    return new Intl.DateTimeFormat("es-PE", {...options, timeZone: "UTC"})
        .format(new Date(`${dateString}T12:00:00Z`));
}

function renderWeekCalendar() {
    const calendar = document.getElementById("calendar-week");
    if (!calendar || !currentWeekStart) return;
    clear(calendar);
    const weekDates = Array.from({length: 7}, (_, index) => shiftDate(currentWeekStart, index));
    const weekEnd = weekDates[6];
    document.getElementById("calendar-week-label").textContent =
        `${calendarDateLabel(currentWeekStart, {day: "numeric", month: "long"})} – ${calendarDateLabel(weekEnd, {day: "numeric", month: "long", year: "numeric"})}`;

    const query = document.getElementById("appointment-search").value.trim().toLocaleLowerCase("es");
    const status = document.getElementById("appointment-filter").value;
    for (const date of weekDates) {
        const day = document.createElement("section");
        day.className = `calendar-day${date === limaToday() ? " calendar-day-today" : ""}`;
        const heading = document.createElement("h3");
        heading.textContent = `${calendarDateLabel(date, {weekday: "short"}).replace(".", "")} ${calendarDateLabel(date, {day: "numeric"})}`;
        day.append(heading);
        const events = appointmentItems
            .filter(item => item.date === date)
            .filter(item => status === "TODAS" || item.status === status)
            .filter(item => `${item.code} ${item.student} ${item.guardian} ${item.teacher} ${item.reason}`
                .toLocaleLowerCase("es").includes(query))
            .sort((left, right) => left.startTime.localeCompare(right.startTime));
        if (!events.length) {
            const empty = document.createElement("p");
            empty.className = "calendar-empty";
            empty.textContent = "Sin citas";
            day.append(empty);
        }
        for (const item of events) {
            const event = document.createElement("article");
            event.className = `calendar-event status-${statusClass(item.status)}`;
            const time = document.createElement("strong");
            time.textContent = `${item.startTime}–${item.endTime}`;
            const student = document.createElement("span");
            student.textContent = item.student;
            const teacher = document.createElement("small");
            teacher.textContent = item.teacher;
            const link = document.createElement("button");
            link.type = "button";
            link.className = "table-action";
            link.textContent = "Ver en la lista";
            link.addEventListener("click", () => {
                document.getElementById("appointment-search").value = item.code;
                filterAppointments();
                document.getElementById("appointments").scrollIntoView({behavior: "smooth", block: "start"});
            });
            event.append(time, student, teacher, link);
            day.append(event);
        }
        calendar.append(day);
    }
}

async function loadBookingOptions(role) {
    const studentSelect = document.getElementById("student");
    const teacherSelect = document.getElementById("teacher");
    clear(studentSelect);
    clear(teacherSelect);

    const source = role === "APODERADO" ? "/mis-estudiantes" : "/relaciones";
    const [students, teachers] = await Promise.all([request(source), request("/docentes")]);
    for (const student of students) {
        if (role === "DOCENTE" || student.canBook) {
            addOption(studentSelect, student.relationId, `${student.student} · ${student.grade}`);
        }
    }
    for (const teacher of teachers) {
        addOption(teacherSelect, teacher.id, teacher.name);
    }
    if (role === "DOCENTE") {
        teacherSelect.value = String(identity.teacherId || "");
        teacherSelect.disabled = true;
    }
    if (!studentSelect.options.length) {
        addOption(studentSelect, "", "No hay relaciones habilitadas para reservar");
        studentSelect.disabled = true;
    }
}

async function loadSlots() {
    const teacherId = document.getElementById("teacher").value;
    const relationId = document.getElementById("student").value;
    const date = document.getElementById("date").value;
    const slotSelect = document.getElementById("slot");
    clear(slotSelect);
    if (!teacherId || !date) {
        addOption(slotSelect, "", "Selecciona docente y fecha");
        return;
    }
    if (!relationId) return;
    const slots = await request(`/disponibilidades?docenteId=${encodeURIComponent(teacherId)}&fecha=${encodeURIComponent(date)}&relacionId=${encodeURIComponent(relationId)}`);
    if (!slots.length) {
        addOption(slotSelect, "", "No hay horarios disponibles");
        return;
    }
    for (const slot of slots) {
        addOption(slotSelect, slot.startTime, `${slot.startTime} – ${slot.endTime}`);
    }
}

function setupBooking(role) {
    const section = document.getElementById("booking");
    section.hidden = false;
    document.getElementById("booking-hint").textContent = role === "APODERADO"
        ? "Elige a tu hijo o hija, el docente y un horario disponible."
        : role === "DOCENTE"
            ? "Solicita una reunión con una familia desde tu propia agenda."
            : "Registra una cita para una familia y un docente habilitado.";
    const hint = document.getElementById("date");
    hint.min = limaToday();

    loadBookingOptions(role).catch(error => showNotice(error.message, true));

    document.getElementById("teacher").addEventListener("change", () => loadSlots().catch(error => showNotice(error.message, true)));
    document.getElementById("date").addEventListener("change", () => loadSlots().catch(error => showNotice(error.message, true)));
    document.getElementById("student").addEventListener("change", () => loadSlots().catch(error => showNotice(error.message, true)));
    document.getElementById("booking-form").addEventListener("submit", async event => {
        event.preventDefault();
        const button = event.currentTarget.querySelector("button[type=submit]");
        button.disabled = true;
        try {
            await request("/citas", {
                method: "POST",
                body: JSON.stringify({
                    studentGuardianRelationId: Number(document.getElementById("student").value),
                    teacherId: Number(document.getElementById("teacher").value),
                    date: document.getElementById("date").value,
                    startTime: document.getElementById("slot").value,
                    modality: document.getElementById("modality").value,
                    reason: document.getElementById("reason").value,
                    comment: document.getElementById("comment").value || null
                })
            });
            showNotice("La solicitud de cita se registró correctamente.");
            document.getElementById("reason").value = "";
            document.getElementById("comment").value = "";
            document.getElementById("slot").value = "";
            await loadSlots();
            await loadAppointments(role);
        } catch (error) {
            showNotice(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
}

async function loadAvailability() {
    const body = document.getElementById("availability-body");
    clear(body);
    const items = await request("/disponibilidades/mias");
    if (!items.length) {
        const row = body.insertRow();
        const cell = row.insertCell();
        cell.colSpan = 7;
        cell.textContent = "Sin horarios registrados. Agrega tu primera ventana de atención.";
        return;
    }
    for (const item of items) {
        const row = body.insertRow();
        row.insertCell().textContent = availabilityTeachers[item.teacherId] || "Mi agenda";
        row.insertCell().textContent = dayNames[item.dayOfWeek] || item.dayOfWeek;
        row.insertCell().textContent = `${item.startTime}–${item.endTime}`;
        row.insertCell().textContent = `${item.validFrom}${item.validUntil ? " → " + item.validUntil : " en adelante"}`;
        row.insertCell().textContent = `${item.durationMinutes} min`;
        row.insertCell().textContent = item.active ? "Activa" : "Desactivada";
        const actions = row.insertCell();
        const edit = document.createElement("button");
        edit.type = "button";
        edit.className = "table-action";
        edit.textContent = "Editar";
        edit.addEventListener("click", () => fillAvailability(item));
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "table-action";
        toggle.textContent = item.active ? "Desactivar" : "Activar";
        toggle.addEventListener("click", async () => {
            toggle.disabled = true;
            try {
                await saveAvailability(item, item.active ? false : true);
                showNotice(item.active ? "Disponibilidad desactivada." : "Disponibilidad activada.");
                await loadAvailability();
            } catch (error) {
                showNotice(error.message, true);
            } finally {
                toggle.disabled = false;
            }
        });
        actions.append(edit, toggle);
    }
}

function fillAvailability(item) {
    document.getElementById("availability-id").value = String(item.id);
    document.getElementById("availability-teacher").value = String(item.teacherId);
    document.getElementById("availability-day").value = String(item.dayOfWeek);
    document.getElementById("availability-start").value = item.startTime.slice(0, 5);
    document.getElementById("availability-end").value = item.endTime.slice(0, 5);
    document.getElementById("availability-from").value = item.validFrom;
    document.getElementById("availability-until").value = item.validUntil || "";
    document.getElementById("availability-duration").value = String(item.durationMinutes);
    document.getElementById("availability-active").value = String(item.active);
    document.getElementById("availability-active-field").hidden = false;
    document.getElementById("availability-reset").hidden = false;
    document.getElementById("availability-submit").textContent = "Guardar cambios";
}

function resetAvailabilityForm() {
    document.getElementById("availability-form").reset();
    document.getElementById("availability-id").value = "";
    document.getElementById("availability-from").value = limaToday();
    document.getElementById("availability-active").value = "true";
    document.getElementById("availability-active-field").hidden = true;
    document.getElementById("availability-reset").hidden = true;
    document.getElementById("availability-submit").textContent = "Agregar horario";
}

async function saveAvailability(item, active) {
    const id = item?.id || document.getElementById("availability-id").value;
    const teacherSelect = document.getElementById("availability-teacher");
    const payload = {
        teacherId: item?.teacherId || identity.teacherId || Number(teacherSelect.value) || null,
        dayOfWeek: item?.dayOfWeek || Number(document.getElementById("availability-day").value),
        startTime: item?.startTime || document.getElementById("availability-start").value,
        endTime: item?.endTime || document.getElementById("availability-end").value,
        validFrom: item?.validFrom || document.getElementById("availability-from").value,
        validUntil: item ? item.validUntil : document.getElementById("availability-until").value || null,
        durationMinutes: item?.durationMinutes || Number(document.getElementById("availability-duration").value)
    };
    if (id) {
        payload.active = active ?? (document.getElementById("availability-active").value === "true");
        await request(`/disponibilidades/${id}`, {method: "PUT", body: JSON.stringify(payload)});
    } else {
        await request("/disponibilidades", {method: "POST", body: JSON.stringify(payload)});
    }
}

async function setupAvailability(role) {
    document.getElementById("availability-section").hidden = false;
    const teacherField = document.getElementById("availability-teacher-field");
    if (isStaff(role)) {
        teacherField.hidden = false;
        const select = document.getElementById("availability-teacher");
        const teachers = await request("/docentes");
        availabilityTeachers = Object.fromEntries(teachers.map(teacher => [teacher.id, teacher.name]));
        clear(select);
        for (const teacher of teachers) addOption(select, teacher.id, teacher.name);
    } else {
        availabilityTeachers[identity.teacherId] = "Mi agenda";
    }
    resetAvailabilityForm();
    loadAvailability().catch(error => showNotice(error.message, true));
    document.getElementById("availability-reset").addEventListener("click", resetAvailabilityForm);
    document.getElementById("availability-form").addEventListener("submit", async event => {
        event.preventDefault();
        const button = event.currentTarget.querySelector("button[type=submit]");
        button.disabled = true;
        try {
            const editing = Boolean(document.getElementById("availability-id").value);
            await saveAvailability(null);
            showNotice(editing ? "Disponibilidad actualizada." : "Horario de atención agregado.");
            resetAvailabilityForm();
            await loadAvailability();
        } catch (error) {
            showNotice(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
}

async function loadImages() {
    const gallery = document.getElementById("image-gallery");
    clear(gallery);
    const images = await request(`/citas/${currentAppointment.id}/imagenes`);
    if (!images.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Esta cita todavía no tiene imágenes adjuntas.";
        gallery.append(empty);
        return;
    }
    for (const image of images) {
        const card = document.createElement("figure");
        card.className = "image-card";
        const link = document.createElement("a");
        link.href = image.url;
        link.target = "_blank";
        link.rel = "noopener";
        const img = document.createElement("img");
        img.src = image.url;
        img.alt = image.originalName || "Imagen de la cita";
        img.loading = "lazy";
        link.append(img);
        const caption = document.createElement("figcaption");
        caption.textContent = image.originalName || "Imagen";
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "table-action";
        remove.textContent = "Eliminar";
        remove.addEventListener("click", async () => {
            if (!window.confirm("¿Eliminar esta imagen de la cita?")) return;
            remove.disabled = true;
            try {
                await request(`/citas/${currentAppointment.id}/imagenes/${image.id}`, {method: "DELETE"});
                showNotice("Imagen eliminada.");
                await loadImages();
            } catch (error) {
                showNotice(error.message, true);
                remove.disabled = false;
            }
        });
        card.append(link, caption, remove);
        gallery.append(card);
    }
}

function openImages(appointment) {
    currentAppointment = appointment;
    document.getElementById("image-modal-title").textContent = `Imágenes · ${appointment.code}`;
    document.getElementById("image-modal-subtitle").textContent =
        `${appointment.student} · ${appointment.teacher} · ${appointment.date}`;
    imageModal.hidden = false;
    loadImages().catch(error => showNotice(error.message, true));
}

function closeImages() {
    imageModal.hidden = true;
    currentAppointment = null;
    document.getElementById("image-upload-form").reset();
}

document.getElementById("image-modal-close").addEventListener("click", closeImages);
imageModal.addEventListener("click", event => {
    if (event.target === imageModal) closeImages();
});
document.getElementById("image-upload-form").addEventListener("submit", async event => {
    event.preventDefault();
    if (!currentAppointment) return;
    const file = document.getElementById("image-input").files[0];
    if (!file) return;
    const body = new FormData();
    body.append("archivo", file);
    const button = event.currentTarget.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        await request(`/citas/${currentAppointment.id}/imagenes`, {method: "POST", body});
        showNotice("Imagen adjuntada a la cita.");
        document.getElementById("image-input").value = "";
        await loadImages();
    } catch (error) {
        showNotice(error.message, true);
    } finally {
        button.disabled = false;
    }
});

async function start() {
    identity = await request("/auth/me");
    const role = identity.roles.map(authority => roleNames[authority]).find(Boolean);
    currentWeekStart = startOfWeek(limaToday());
    renderWeekCalendar();
    if (role === "APODERADO" || role === "DOCENTE" || isStaff(role)) {
        setupBooking(role);
        document.getElementById("new-appointment-link").hidden = false;
    }
    if (role === "DOCENTE" || isStaff(role)) {
        await setupAvailability(role);
    }
    document.getElementById("refresh").addEventListener("click", () => loadAppointments(role));
    document.getElementById("appointment-search").addEventListener("input", filterAppointments);
    document.getElementById("appointment-filter").addEventListener("change", filterAppointments);
    document.getElementById("calendar-previous").addEventListener("click", () => {
        currentWeekStart = shiftDate(currentWeekStart || startOfWeek(limaToday()), -7);
        renderWeekCalendar();
    });
    document.getElementById("calendar-next").addEventListener("click", () => {
        currentWeekStart = shiftDate(currentWeekStart || startOfWeek(limaToday()), 7);
        renderWeekCalendar();
    });
    document.getElementById("calendar-today").addEventListener("click", () => {
        currentWeekStart = startOfWeek(limaToday());
        renderWeekCalendar();
    });
    await loadAppointments(role);
}

start().catch(error => showNotice(error.message, true));
