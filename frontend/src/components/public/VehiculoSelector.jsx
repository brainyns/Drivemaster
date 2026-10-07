import { useState, useEffect } from "react";
import { misVehiculos, repuestosCompatibles } from "../../services/vehiculoService";
import Icon from "./Icon";
import "../../css/vehiculo-selector.css";

/**
 * Panel "¿Qué vehículo tienes?" del mockup, conectado a la API real.
 *
 * A diferencia del prototipo — que pedía marca/modelo/año/motor con selects
 * anidados — aquí el cliente ya tiene sus vehículos registrados, así que el
 * panel los lista y al elegir uno se consultan sus repuestos compatibles.
 * Nunca se muestra compatibilidad inventada: si no hay vehículos registrados
 * o no hay sesión, el panel ofrece el camino para registrar uno.
 */
export default function VehiculoSelector({
  token,
  vehiculo,
  idsCompatibles,
  onSelect,
  onIrLogin,
  onIrVehiculos,
  onVerCatalogo,
}) {
  // La respuesta se etiqueta con el token al que pertenece: así el estado de
  // carga y la lista se derivan, sin setState sincrónico dentro del efecto.
  const [respuesta, setRespuesta] = useState(null);

  useEffect(() => {
    if (!token) return;
    let vivo = true;

    misVehiculos(token)
      .then((data) => {
        if (vivo) setRespuesta({ token, lista: Array.isArray(data) ? data : [], error: null });
      })
      .catch((e) => {
        if (vivo) setRespuesta({ token, lista: [], error: e.message });
      });

    return () => {
      vivo = false;
    };
  }, [token]);

  const hayToken = !!token;
  const cargando = hayToken && (respuesta === null || respuesta.token !== token);
  const error = respuesta?.token === token ? respuesta.error : null;
  const vehiculos = respuesta?.token === token ? respuesta.lista : [];

  const elegir = async (v) => {
    if (vehiculo?.id === v.id) {
      onSelect(null, null);
      return;
    }
    try {
      const compatibles = await repuestosCompatibles(token, v.id);
      const lista = Array.isArray(compatibles) ? compatibles : [];
      onSelect(
        v,
        new Set(
          lista
            .map((p) => (typeof p === "object" && p ? p.id : p))
            .filter((id) => id != null)
        )
      );
    } catch {
      // Si la consulta de compatibilidad falla, mostramos el vehículo sin
      // filtrar en vez de ocultarlo.
      onSelect(v, null);
    }
  };

  return (
    <div className="dm-vehicle">
      <div className="dm-vehicle__panel">
        <div className="dm-vehicle__heading">
          <div className="dm-vehicle__title">
            <div className="dm-icon-tile">
              <Icon name="car" size={25} />
            </div>
            <div>
              <h2>¿Qué vehículo tienes?</h2>
              <p>La pieza correcta comienza por conocerlo.</p>
            </div>
          </div>
          <span className="dm-vehicle__hint">
            <Icon name="shield" size={15} /> Compatibilidad, no suposiciones.
          </span>
        </div>

        {!hayToken && (
          <div className="dm-vehicle__empty">
            <p>
              Identifica tu vehículo para ver solo los repuestos que realmente
              le encajan.
            </p>
            <button className="dm-btn dm-btn--primary" onClick={onIrLogin}>
              Iniciar sesión <Icon name="arrow" size={18} />
            </button>
          </div>
        )}

        {hayToken && cargando && (
          <div className="dm-vehicle__empty">
            <p>Cargando tus vehículos...</p>
          </div>
        )}

        {hayToken && !cargando && vehiculos.length === 0 && (
          <div className="dm-vehicle__empty">
            <p>
              {error
                ? `No pudimos cargar tus vehículos: ${error}`
                : "Aún no tienes vehículos registrados. Agrega el tuyo y te mostramos solo lo compatible."}
            </p>
            <button className="dm-btn dm-btn--primary" onClick={onIrVehiculos}>
              {error ? "Reintentar" : "Agregar mi vehículo"} <Icon name="arrow" size={18} />
            </button>
          </div>
        )}

        {hayToken && !cargando && vehiculos.length > 0 && (
          <>
            <div className="dm-vehicle__list" role="listbox" aria-label="Tus vehículos">
              {vehiculos.map((v) => {
                const activo = vehiculo?.id === v.id;
                return (
                  <button
                    key={v.id}
                    role="option"
                    aria-selected={activo}
                    className={`dm-vehicle__item ${activo ? "is-active" : ""}`}
                    onClick={() => elegir(v)}
                  >
                    {v.imagenUrl ? (
                      <img className="dm-vehicle__photo" src={v.imagenUrl} alt={`${v.marca} ${v.modelo}`} />
                    ) : (
                      <span className="dm-vehicle__photo is-empty">
                        <Icon name="car" size={22} />
                      </span>
                    )}
                    <span className="dm-vehicle__meta">
                      <strong>{v.marca} {v.modelo}</strong>
                      <small>
                        {[v.anio, v.placa].filter(Boolean).join(" · ")}
                        {v.motor ? ` · ${v.motor}` : ""}
                      </small>
                    </span>
                    {activo ? (
                      <span className="dm-vehicle__check"><Icon name="check" size={16} /></span>
                    ) : (
                      <Icon name="arrow" size={16} className="dm-vehicle__go" />
                    )}
                  </button>
                );
              })}
            </div>

            {vehiculo && (
              <div className="dm-vehicle__active">
                <Icon name="shield" size={16} />
                <span>
                  Mostrando <strong>{idsCompatibles ? idsCompatibles.size : "todos"}</strong>{" "}
                  repuestos compatibles con {vehiculo.marca} {vehiculo.modelo}.
                </span>
                <button onClick={() => onSelect(null, null)}>Quitar filtro</button>
              </div>
            )}
          </>
        )}

        <div className="dm-vehicle__footnote">
          <Icon name="check" size={13} />
          <span>Solo te mostraremos repuestos que se ajustan a tu vehículo.</span>
          <button onClick={onVerCatalogo}>
            ¿Ya conoces la referencia? Búscala aquí <Icon name="arrow" size={13} />
          </button>
        </div>
      </div>
    </div>
  );
}