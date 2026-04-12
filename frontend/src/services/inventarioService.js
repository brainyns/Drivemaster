const API = "http://localhost:8080/inventario";

export const ajustarStock = async (productoId, nuevoStock, motivo) => {

    const response = await fetch(
        `${API}/ajuste/${productoId}/${nuevoStock}?motivo=${motivo}`,
        {
            method: "PUT"
        }
    );

    if (!response.ok) {
        throw new Error("Error ajustando inventario");
    }

    return await response.text();

};