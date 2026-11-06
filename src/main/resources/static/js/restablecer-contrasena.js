function csrfToken() {
    const cookie = document.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="));
    return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : "";
}

const form = document.getElementById("reset-form");
const notice = document.getElementById("reset-notice");
const token = new URLSearchParams(window.location.search).get("token") || "";

form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!token) {
        notice.textContent = "El enlace está incompleto. Solicita uno nuevo.";
        notice.className = "alert alert-error";
        notice.hidden = false;
        return;
    }
    const newPassword = document.getElementById("new-password").value;
    if (newPassword !== document.getElementById("confirm-password").value) {
        notice.textContent = "Las contraseñas no coinciden.";
        notice.className = "alert alert-error";
        notice.hidden = false;
        return;
    }
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        const response = await fetch("/api/v1/auth/restablecer-contrasena", {
            method: "POST",
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrfToken()
            },
            body: JSON.stringify({token, newPassword})
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || "No se pudo actualizar la contraseña.");
        notice.textContent = result.data.message;
        notice.className = "alert alert-success";
        form.reset();
        history.replaceState(null, "", window.location.pathname);
    } catch (error) {
        notice.textContent = error.message;
        notice.className = "alert alert-error";
    } finally {
        notice.hidden = false;
        button.disabled = false;
    }
});
