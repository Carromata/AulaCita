const api = "/api/v1";
const notice = document.getElementById("notice");
const list = document.getElementById("notification-list");
const filter = document.getElementById("notification-filter");

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

function formatDate(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString("es-PE", {dateStyle: "medium", timeStyle: "short"});
}

function render(items) {
    clear(list);
    if (!items.length) {
        const empty = document.createElement("p");
        empty.className = "muted empty-state";
        empty.textContent = filter.value === "unread"
            ? "No tienes notificaciones sin leer."
            : "Todavía no tienes notificaciones.";
        list.append(empty);
        return;
    }
    for (const item of items) {
        const article = document.createElement("article");
        article.className = `notification-item${item.read ? "" : " unread"}`;

        const body = document.createElement("div");
        body.className = "notification-body";
        const heading = document.createElement("div");
        heading.className = "notification-heading";
        const title = document.createElement("strong");
        title.textContent = item.title;
        const type = document.createElement("span");
        type.className = `status-badge status-${(item.type || "INFO").toLocaleLowerCase("es")}`;
        type.textContent = item.type === "AVISO" ? "Aviso" : "Cita";
        heading.append(title, type);
        const message = document.createElement("p");
        message.textContent = item.message;
        const meta = document.createElement("span");
        meta.className = "notification-meta";
        meta.textContent = [formatDate(item.createdAt), item.appointmentCode ? `Cita ${item.appointmentCode}` : ""]
            .filter(Boolean).join(" · ");
        body.append(heading, message, meta);

        const actions = document.createElement("div");
        actions.className = "notification-actions";
        if (!item.read) {
            const read = document.createElement("button");
            read.type = "button";
            read.className = "button button-outline button-small";
            read.textContent = "Marcar leída";
            read.addEventListener("click", async () => {
                read.disabled = true;
                try {
                    await request(`/notificaciones/${item.id}/leer`, {method: "POST"});
                    await load();
                    if (window.refreshNotificationBadge) window.refreshNotificationBadge();
                } catch (error) {
                    showNotice(error.message, true);
                    read.disabled = false;
                }
            });
            actions.append(read);
        }
        if (item.appointmentId) {
            const link = document.createElement("a");
            link.className = "button button-small";
            link.href = "/citas";
            link.textContent = "Ver cita";
            actions.append(link);
        }
        article.append(body, actions);
        list.append(article);
    }
}

async function load() {
    const onlyUnread = filter.value === "unread";
    const items = await request(`/notificaciones?soloNoLeidas=${onlyUnread}&limite=60`);
    render(items);
}

filter.addEventListener("change", () => load().catch(error => showNotice(error.message, true)));

document.getElementById("mark-all-read").addEventListener("click", async event => {
    event.currentTarget.disabled = true;
    try {
        await request("/notificaciones/leer-todas", {method: "POST"});
        showNotice("Todas las notificaciones quedaron como leídas.");
        await load();
        if (window.refreshNotificationBadge) window.refreshNotificationBadge();
    } catch (error) {
        showNotice(error.message, true);
    } finally {
        event.currentTarget.disabled = false;
    }
});

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
            await load();
        } catch (error) {
            showNotice(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
}

load().catch(error => showNotice(error.message, true));
