const loginForm = document.querySelector(".login-card form");
const passwordInput = document.getElementById("password");
const passwordToggle = document.querySelector(".password-toggle");
const submitButton = document.querySelector(".login-submit");

if (passwordInput instanceof HTMLInputElement && passwordToggle instanceof HTMLButtonElement) {
    passwordToggle.addEventListener("click", () => {
        const isVisible = passwordInput.type === "text";
        passwordInput.type = isVisible ? "password" : "text";
        passwordToggle.textContent = isVisible ? "Mostrar" : "Ocultar";
        passwordToggle.setAttribute("aria-pressed", String(!isVisible));
        passwordInput.focus();
    });
}

if (loginForm instanceof HTMLFormElement && submitButton instanceof HTMLButtonElement) {
    loginForm.addEventListener("submit", () => {
        if (!loginForm.reportValidity()) {
            return;
        }
        submitButton.disabled = true;
        submitButton.textContent = "Ingresando…";
    });

    window.addEventListener("pageshow", () => {
        submitButton.disabled = false;
        submitButton.textContent = "Ingresar";
    });
}
