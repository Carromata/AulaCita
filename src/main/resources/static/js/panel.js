const layout = document.getElementById("app-layout");
const sidebarToggle = document.getElementById("sidebar-toggle");
const sidebarPreferenceKey = "aulacita.sidebar.hidden";

function setSidebarHidden(hidden, persist = true) {
    layout.classList.toggle("sidebar-hidden", hidden);
    sidebarToggle.setAttribute("aria-expanded", String(!hidden));
    sidebarToggle.setAttribute("aria-label", hidden ? "Mostrar menú" : "Ocultar menú");
    sidebarToggle.querySelector(".toggle-label").textContent = hidden ? "Mostrar menú" : "Ocultar menú";
    if (persist) {
        try {
            localStorage.setItem(sidebarPreferenceKey, String(hidden));
        } catch {
            // El panel sigue funcionando aunque el navegador bloquee el almacenamiento local.
        }
    }
}

let startsHidden = window.matchMedia("(max-width: 760px)").matches;
try {
    const saved = localStorage.getItem(sidebarPreferenceKey);
    if (saved !== null) startsHidden = saved === "true";
} catch {
    // Se conserva el valor inicial según el tamaño de pantalla.
}

setSidebarHidden(startsHidden, false);
sidebarToggle.addEventListener("click", () => {
    setSidebarHidden(!layout.classList.contains("sidebar-hidden"));
});

/* El resumen usa los mismos datos y límites de acceso que la agenda. */
(function loadDashboardPreview() {
    const appointmentsList = document.getElementById("dashboard-appointments");
    const notificationsList = document.getElementById("dashboard-notifications");
    if (!appointmentsList || !notificationsList) return;

    async function getData(path) {
        const response = await fetch(`/api/v1${path}`, {
            headers: {Accept: "application/json"}
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload?.message || "No se pudo cargar la información del panel.");
        }
        return payload.data;
    }

    function renderEmpty(container, message) {
        container.replaceChildren();
        const empty = document.createElement("p");
        empty.className = "muted empty-state";
        empty.textContent = message;
        container.append(empty);
    }

    function renderAppointments(items) {
        const role = appointmentsList.dataset.role;
        const today = new Intl.DateTimeFormat("en-CA", {timeZone: "America/Lima"}).format(new Date());
        const upcoming = items
            .filter(item => item.date >= today && ["Pendiente", "Confirmada"].includes(item.status))
            .sort((left, right) => `${left.date}T${left.startTime}`.localeCompare(`${right.date}T${right.startTime}`))
            .slice(0, 5);

        if (!upcoming.length) {
            renderEmpty(appointmentsList, "No tienes citas pendientes o confirmadas próximas.");
            return;
        }

        appointmentsList.replaceChildren();
        for (const appointment of upcoming) {
            const article = document.createElement("article");
            article.className = "dashboard-appointment";

            const date = document.createElement("div");
            date.className = "dashboard-appointment-date";
            const dateValue = document.createElement("strong");
            dateValue.textContent = appointment.date.slice(8, 10);
            const dateMonth = document.createElement("span");
            dateMonth.textContent = new Intl.DateTimeFormat("es-PE", {
                month: "short", timeZone: "UTC"
            }).format(new Date(`${appointment.date}T12:00:00Z`)).replace(".", "");
            date.append(dateValue, dateMonth);

            const details = document.createElement("div");
            details.className = "dashboard-appointment-details";
            const title = document.createElement("strong");
            title.textContent = appointment.student;
            const people = document.createElement("span");
            people.textContent = role === "APODERADO"
                ? `Docente: ${appointment.teacher}`
                : role === "DOCENTE"
                    ? `Apoderado: ${appointment.guardian}`
                    : `${appointment.teacher} · ${appointment.guardian}`;
            const time = document.createElement("span");
            time.textContent = `${appointment.startTime} · ${appointment.code}`;
            details.append(title, people, time);

            const status = document.createElement("span");
            status.className = `status-badge status-${appointment.status.toLocaleLowerCase("es")}`;
            status.textContent = appointment.status;
            article.append(date, details, status);
            appointmentsList.append(article);
        }
    }

    function renderNotifications(items) {
        if (!items.length) {
            renderEmpty(notificationsList, "No tienes avisos recientes.");
            return;
        }
        notificationsList.replaceChildren();
        for (const item of items.slice(0, 3)) {
            const article = document.createElement("article");
            article.className = `dashboard-notice${item.read ? "" : " unread"}`;
            const title = document.createElement("strong");
            title.textContent = item.title;
            const message = document.createElement("p");
            message.textContent = item.message;
            article.append(title, message);
            notificationsList.append(article);
        }
    }

    if (appointmentsList.dataset.role === "DIRECTOR") {
        renderEmpty(appointmentsList, "Tu rol tiene acceso a los reportes agregados de la institución.");
    } else {
        getData("/citas?pagina=0&limite=100")
            .then(renderAppointments)
            .catch(() => renderEmpty(appointmentsList, "No se pudieron cargar las citas. Actualiza el panel para intentar de nuevo."));
    }

    getData("/notificaciones/panel")
        .then(renderNotifications)
        .catch(() => renderEmpty(notificationsList, "No se pudieron cargar las notificaciones."));
})();

/* Notificaciones: badge global y modal de avisos pendientes. */
(function () {
    const badge = document.getElementById("nav-notification-badge");
    const dismissedKey = "aulacita.notif.dismissed";

    function readCookie(name) {
        const cookie = document.cookie.split("; ").find(value => value.startsWith(name + "="));
        return cookie ? decodeURIComponent(cookie.substring(name.length + 1)) : "";
    }

    async function apiFetch(path, options = {}) {
        const headers = new Headers(options.headers || {});
        headers.set("Accept", "application/json");
        if (options.body) headers.set("Content-Type", "application/json");
        if (options.method && options.method !== "GET") {
            headers.set("X-XSRF-TOKEN", readCookie("XSRF-TOKEN"));
        }
        const response = await fetch(`/api/v1${path}`, {...options, headers});
        const payload = response.status === 204 ? null : await response.json();
        if (!response.ok) throw new Error(payload?.message || "No se pudo consultar las notificaciones.");
        return payload?.data;
    }

    function dismissedIds() {
        try {
            return new Set(JSON.parse(sessionStorage.getItem(dismissedKey) || "[]"));
        } catch {
            return new Set();
        }
    }

    function dismiss(id) {
        const ids = dismissedIds();
        ids.add(id);
        try {
            sessionStorage.setItem(dismissedKey, JSON.stringify([...ids]));
        } catch {
            // La sesión sigue funcionando aunque el navegador bloquee el almacenamiento.
        }
    }

    window.refreshNotificationBadge = async function () {
        if (!badge) return;
        try {
            const summary = await apiFetch("/notificaciones/resumen");
            const unread = Number(summary?.unread || 0);
            badge.textContent = String(unread);
            badge.hidden = unread === 0;
        } catch {
            badge.hidden = true;
        }
    };

    function buildModal(items) {
        const backdrop = document.createElement("div");
        backdrop.className = "modal-backdrop";
        backdrop.setAttribute("role", "presentation");

        const dialog = document.createElement("section");
        dialog.className = "modal-dialog";
        dialog.setAttribute("role", "dialog");
        dialog.setAttribute("aria-modal", "true");
        dialog.setAttribute("aria-labelledby", "notif-modal-title");

        const header = document.createElement("div");
        header.className = "modal-header";
        const copy = document.createElement("div");
        const kicker = document.createElement("p");
        kicker.className = "eyebrow";
        kicker.textContent = "AVISOS DE AULACITA";
        const title = document.createElement("h2");
        title.id = "notif-modal-title";
        title.textContent = items.length === 1 ? "Tienes una notificación nueva" : `Tienes ${items.length} notificaciones nuevas`;
        copy.append(kicker, title);
        const close = document.createElement("button");
        close.type = "button";
        close.className = "modal-close";
        close.setAttribute("aria-label", "Cerrar");
        close.textContent = "×";
        header.append(copy, close);

        const body = document.createElement("div");
        body.className = "modal-body notification-list";
        for (const item of items) {
            const article = document.createElement("article");
            article.className = "notification-item unread";
            const heading = document.createElement("div");
            heading.className = "notification-heading";
            const itemTitle = document.createElement("strong");
            itemTitle.textContent = item.title;
            const type = document.createElement("span");
            type.className = `status-badge status-${(item.type || "INFO").toLocaleLowerCase("es")}`;
            type.textContent = item.type === "AVISO" ? "Aviso" : "Cita";
            heading.append(itemTitle, type);
            const message = document.createElement("p");
            message.textContent = item.message;
            article.append(heading, message);
            body.append(article);
        }

        const footer = document.createElement("div");
        footer.className = "modal-footer";
        const later = document.createElement("button");
        later.type = "button";
        later.className = "button button-outline";
        later.textContent = "Revisar después";
        const accept = document.createElement("button");
        accept.type = "button";
        accept.className = "button";
        accept.textContent = "Entendido";
        if (items.some(item => item.appointmentId)) {
            const go = document.createElement("a");
            go.className = "button button-outline";
            go.href = "/citas";
            go.textContent = "Ir a mis citas";
            footer.append(go);
        }
        footer.append(later, accept);
        dialog.append(header, body, footer);
        backdrop.append(dialog);

        const closeModal = () => backdrop.remove();
        const acknowledge = async () => {
            accept.disabled = true;
            for (const item of items) {
                dismiss(item.id);
                try {
                    await apiFetch(`/notificaciones/${item.id}/leer`, {method: "POST"});
                } catch {
                    // El aviso se cierra igual en esta sesión.
                }
            }
            closeModal();
            window.refreshNotificationBadge();
        };
        close.addEventListener("click", closeModal);
        later.addEventListener("click", closeModal);
        accept.addEventListener("click", acknowledge);
        backdrop.addEventListener("click", event => {
            if (event.target === backdrop) closeModal();
        });
        document.addEventListener("keydown", function onEscape(event) {
            if (event.key === "Escape") {
                closeModal();
                document.removeEventListener("keydown", onEscape);
            }
        }, {once: true});

        document.body.append(backdrop);
        accept.focus();
    }

    async function showPendingModal() {
        if (window.location.pathname.endsWith("/notificaciones")) return;
        const items = await apiFetch("/notificaciones?soloNoLeidas=true&limite=5");
        const shown = dismissedIds();
        const pending = (items || []).filter(item => !shown.has(item.id));
        if (pending.length) buildModal(pending);
    }

    window.refreshNotificationBadge();
    if (document.readyState === "complete") {
        showPendingModal().catch(() => {});
    } else {
        window.addEventListener("load", () => showPendingModal().catch(() => {}));
    }
    setInterval(() => window.refreshNotificationBadge(), 60000);
})();
