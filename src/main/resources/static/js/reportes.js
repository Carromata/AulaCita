const reportForm = document.getElementById("report-form");
const reportNotice = document.getElementById("report-notice");
const results = document.getElementById("report-results");
const submitButton = document.getElementById("report-submit");
const exportButton = document.getElementById("report-export");
let currentReport = null;

function schoolDate() {
    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "America/Lima", year: "numeric", month: "2-digit", day: "2-digit"
    }).format(new Date());
}

function showReportMessage(message, error = false) {
    reportNotice.textContent = message;
    reportNotice.className = `alert ${error ? "alert-error" : "alert-success"}`;
    reportNotice.hidden = false;
}

function clearResults(message) {
    results.replaceChildren();
    const row = results.insertRow();
    const cell = row.insertCell();
    cell.colSpan = 2;
    cell.textContent = message;
}

function renderStatusChart(items) {
    const chart = document.getElementById("status-chart");
    chart.replaceChildren();
    const total = items.reduce((sum, item) => sum + item.count, 0);
    if (!items.length || total === 0) {
        const empty = document.createElement("p");
        empty.className = "muted empty-state";
        empty.textContent = "No hay citas registradas en este período.";
        chart.append(empty);
        return;
    }

    for (const [index, item] of items.entries()) {
        const row = document.createElement("div");
        row.className = `status-chart-row status-chart-color-${index % 4}`;
        const heading = document.createElement("div");
        heading.className = "status-chart-heading";
        const label = document.createElement("strong");
        label.textContent = item.status;
        const value = document.createElement("span");
        const percent = Math.round(item.count / total * 100);
        value.textContent = `${new Intl.NumberFormat("es-PE").format(item.count)} · ${percent}%`;
        heading.append(label, value);

        const track = document.createElement("div");
        track.className = "status-chart-track";
        const bar = document.createElement("span");
        bar.style.width = `${percent}%`;
        track.append(bar);
        row.append(heading, track);
        chart.append(row);
    }
}

function csvCell(value) {
    let safe = String(value ?? "");
    if (/^[\s]*[=+@\-]/.test(safe)) safe = `'${safe}`;
    return `"${safe.replaceAll('"', '""')}"`;
}

function exportReport() {
    if (!currentReport) return;
    const rows = [
        ["Período desde", currentReport.from],
        ["Período hasta", currentReport.to],
        [],
        ["Estado", "Cantidad"],
        ...currentReport.appointmentsByStatus.map(item => [item.status, item.count]),
        ["Total", currentReport.appointmentsByStatus.reduce((sum, item) => sum + item.count, 0)]
    ];
    const csv = rows.map(row => row.map(csvCell).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob(["\uFEFF", csv], {type: "text/csv;charset=utf-8"}));
    const link = document.createElement("a");
    link.href = url;
    link.download = `aulacita-reporte-${currentReport.from}-${currentReport.to}.csv`;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

document.getElementById("from").value = `${schoolDate().slice(0, 7)}-01`;
document.getElementById("to").value = schoolDate();
exportButton.addEventListener("click", exportReport);

reportForm.addEventListener("submit", async event => {
    event.preventDefault();
    const from = document.getElementById("from").value;
    const to = document.getElementById("to").value;
    const period = document.getElementById("period");
    if (from > to) {
        showReportMessage("La fecha inicial debe ser anterior o igual a la fecha final.", true);
        return;
    }
    const days = (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000;
    if (days > 366) {
        showReportMessage("El rango no puede superar los 367 días.", true);
        return;
    }
    submitButton.disabled = true;
    submitButton.textContent = "Consultando…";
    exportButton.disabled = true;
    currentReport = null;
    clearResults("Consultando información del sistema…");
    try {
        const response = await fetch(`/api/v1/reportes/resumen?desde=${encodeURIComponent(from)}&hasta=${encodeURIComponent(to)}`, {
            headers: {"Accept": "application/json"}
        });
        const payload = await response.json();
        if (!response.ok) throw new Error(payload.message || "No se pudo generar el reporte.");
        const report = payload.data;
        if (!report || !Array.isArray(report.appointmentsByStatus)) throw new Error("La respuesta del reporte no tiene el formato esperado.");
        currentReport = report;
        period.textContent = `${report.from} a ${report.to}`;
        clearResults("No hay citas en el período seleccionado.");
        if (report.appointmentsByStatus.length) {
            results.replaceChildren();
            for (const item of report.appointmentsByStatus) {
                const row = results.insertRow();
                row.insertCell().textContent = item.status;
                row.insertCell().textContent = new Intl.NumberFormat("es-PE").format(item.count);
            }
        }
        const total = report.appointmentsByStatus.reduce((sum, item) => sum + item.count, 0);
        document.getElementById("report-total").textContent = new Intl.NumberFormat("es-PE").format(total);
        document.getElementById("report-status-count").textContent = String(report.appointmentsByStatus.length);
        document.getElementById("report-insight-total").textContent = new Intl.NumberFormat("es-PE").format(total);
        document.getElementById("report-insight-period").textContent = `${report.from} — ${report.to}`;
        document.getElementById("period-chip").textContent = `${report.from} — ${report.to}`;
        renderStatusChart(report.appointmentsByStatus);
        exportButton.disabled = false;
        showReportMessage("El reporte se consultó correctamente.");
    } catch (error) {
        clearResults("No se pudo cargar el reporte.");
        document.getElementById("report-total").textContent = "—";
        document.getElementById("report-status-count").textContent = "—";
        document.getElementById("report-insight-total").textContent = "—";
        document.getElementById("report-insight-period").textContent = "—";
        renderStatusChart([]);
        period.textContent = "";
        showReportMessage(error.message || "No se pudo generar el reporte.", true);
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Consultar";
    }
});

reportForm.requestSubmit();
