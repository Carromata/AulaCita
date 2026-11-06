const form = document.getElementById("password-form");
const profileForm = document.getElementById("profile-form");
const notice = document.getElementById("profile-notice");
const api = "/api/v1";

function csrfToken() {
    const cookie = document.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="));
    return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : "";
}

async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    if (options.body) headers.set("Content-Type", "application/json");
    if (options.method && options.method !== "GET") headers.set("X-XSRF-TOKEN", csrfToken());
    const response = await fetch(`${api}${path}`, {...options, headers});
    const payload = response.status === 204 ? null : await response.json();
    if (!response.ok) throw new Error(payload?.message || `No se pudo completar la solicitud (${response.status}).`);
    return payload?.data;
}

function showNotice(message, isError = false) {
    notice.textContent = message;
    notice.className = `alert ${isError ? "alert-error" : "alert-success"}`;
    notice.hidden = false;
}

function fillProfile(profile) {
    document.getElementById("profile-email").value = profile.email || "";
    document.getElementById("profile-role").value = profile.role || "";
    document.getElementById("profile-first").value = profile.firstName || "";
    document.getElementById("profile-last").value = profile.lastName || "";
    document.getElementById("profile-mother").value = profile.motherLastName || "";
    document.getElementById("profile-phone").value = profile.phone || "";
    document.getElementById("profile-document-type").value = profile.documentType || "";
    document.getElementById("profile-document-number").value = profile.documentNumber || "";
}

profileForm.addEventListener("submit", async event => {
    event.preventDefault();
    const button = profileForm.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        const profile = await request("/auth/perfil", {
            method: "PUT",
            body: JSON.stringify({
                firstName: document.getElementById("profile-first").value.trim(),
                lastName: document.getElementById("profile-last").value.trim(),
                motherLastName: document.getElementById("profile-mother").value.trim() || null,
                phone: document.getElementById("profile-phone").value.trim() || null,
                documentType: document.getElementById("profile-document-type").value || null,
                documentNumber: document.getElementById("profile-document-number").value.trim() || null
            })
        });
        fillProfile(profile);
        showNotice("Tus datos se actualizaron correctamente.");
    } catch (error) {
        showNotice(error.message, true);
    } finally {
        button.disabled = false;
    }
});

async function loadProfile() {
    try {
        fillProfile(await request("/auth/perfil"));
    } catch (error) {
        showNotice(error.message, true);
    }
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    const currentPassword = document.getElementById("currentPassword").value;
    const newPassword = document.getElementById("newPassword").value;
    const confirmation = document.getElementById("confirmPassword").value;
    if (newPassword !== confirmation) {
        showNotice("Las contraseñas nuevas no coinciden.", true);
        return;
    }
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        const result = await request("/auth/cambiar-contrasena", {
            method: "POST",
            body: JSON.stringify({currentPassword, newPassword})
        });
        form.reset();
        showNotice(result.message);
    } catch (error) {
        showNotice(error.message, true);
    } finally {
        button.disabled = false;
    }
});

loadProfile();
