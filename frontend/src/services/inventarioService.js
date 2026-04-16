const API = "http://localhost:8080/inventario";

const buildHeaders = (token, contentType) => {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (contentType) headers["Content-Type"] = contentType;
  return headers;
};

export const ajustarStock = async (productoId, nuevoStock, motivo, token) => {

    const response = await fetch(
        `${API}/ajuste/${productoId}/${nuevoStock}?motivo=${motivo}`,
        {
            method: "PUT",
            headers: buildHeaders(token),
        }
    );

    if (!response.ok) {
        throw new Error("Error ajustando inventario");
    }

    return await response.text();

};