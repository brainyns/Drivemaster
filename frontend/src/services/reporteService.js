import { apiFetch } from "./http";
const BASE_URL = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/reportes`;

const buildHeaders = (token) => {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

export async function getReportesServicios(token, periodo = "mes") {
  const res = await apiFetch(`${BASE_URL}/servicios/reportes?periodo=${periodo}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener el reporte de servicios");
  return res.json();
}