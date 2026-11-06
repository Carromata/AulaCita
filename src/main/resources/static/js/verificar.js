const form = document.getElementById("verify-form");
const notice = document.getElementById("notice");
const result = document.getElementById("result");
const resultTitle = document.getElementById("result-title");
const resultBody = document.getElementById("result-body");

function showNotice(message, isError = false) {
    notice.textContent = message;
    notice.className = `alert ${isError ? "alert-error" : "alert-success"}`;
    notice.hidden = false;
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    notice.hidden = true;
    result.hidden = true;
    const dni = document.getElementById("dni").value.trim();
    if (!/^\d{8}$/.test(dni)) {
        showNotice("El DNI debe tener exactamente 8 dígitos.", true);
        return;
    }
    try {
        const response = await fetch(`/api/v1/estudiantes/verificar?dni=${encodeURIComponent(dni)}`, {
            headers: {Accept: "application/json"}
        });
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload?.message || "No se pudo verificar el DNI.");
        }
        const data = payload.data;
        result.hidden = false;
        if (data.exists) {
            resultTitle.textContent = "Alumno encontrado";
            resultBody.textContent = `${data.fullName} · ${data.gradeLabel} · DNI ${data.dni}`;
            showNotice("El alumno pertenece a la institución.");
        } else {
            resultTitle.textContent = "No registrado";
            resultBody.textContent = `No hay un alumno activo con DNI ${dni} en la institución.`;
            showNotice("No se encontró al alumno.", true);
        }
    } catch (error) {
        showNotice(error.message || "No se pudo verificar el DNI.", true);
    }
});
