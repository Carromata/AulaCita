function csrfToken() {
    const cookie = document.cookie.split("; ").find(value => value.startsWith("XSRF-TOKEN="));
    return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : "";
}

const form = document.getElementById("recovery-form");
const notice = document.getElementById("recovery-notice");

form.addEventListener("submit", async event => {
    event.preventDefault();
    const button = form.querySelector("button[type=submit]");
    button.disabled = true;
    try {
        const response = await fetch("/api/v1/auth/recuperar-contrasena", {
            method: "POST",
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrfToken()
            },
            body: JSON.stringify({email: document.getElementById("recovery-email").value.trim()})
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || "No se pudo solicitar la recuperación.");
        notice.textContent = result.data.message;
        notice.className = "alert alert-success";
        form.reset();
    } catch (error) {
        notice.textContent = error.message;
        notice.className = "alert alert-error";
    } finally {
        notice.hidden = false;
        button.disabled = false;
    }
});
