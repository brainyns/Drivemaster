import { useEffect, useMemo, useRef, useState } from "react";
import { CircleMarker, MapContainer, Marker, Popup, TileLayer, Tooltip, useMap } from "react-leaflet";
import { talleresMapa } from "../../services/tallerService";
import { TILES_ATRIBUCION, TILES_URL, centroDeCiudad } from "../../utils/mapa";
import { distanciaKm, formatoDistancia } from "../../utils/distancia";
import { iconoElegido, iconoNormal } from "../../utils/leafletIcon";
import "../../css/mapa.css";

/** Centra el mapa en la ubicación del cliente cuando llega. */
function CentrarEnCliente({ ubicacion }) {
  const map = useMap();
  useEffect(() => {
    if (ubicacion) map.flyTo([ubicacion.latitud, ubicacion.longitud], 13, { duration: 0.6 });
  }, [ubicacion, map]);
  return null;
}

/**
 * Mapa y lista de talleres para que el cliente elija uno al pedir un diagnóstico.
 * valor: id del taller elegido, o null = asignación automática. onElegir(id | null).
 * ubicacion: { latitud, longitud } del cliente (opcional); ordena por distancia.
 */
export default function MapaTalleres({ token, valor, onElegir, ubicacion, ciudad }) {
  const [talleres, setTalleres] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [intento, setIntento] = useState(0);
  const [mapa, setMapa] = useState(null);
  const [centroInicial] = useState(() => centroDeCiudad(ciudad));
  const marcadores = useRef({});
  const contenedorMapa = useRef(null);

  useEffect(() => {
    let vivo = true;
    talleresMapa(token)
      .then((lista) => { if (vivo) { setTalleres(lista || []); setError(null); } })
      .catch((e) => { if (vivo) setError(e.message); })
      .finally(() => { if (vivo) setCargando(false); });
    return () => { vivo = false; };
  }, [token, intento]);

  const reintentar = () => {
    setCargando(true);
    setIntento((n) => n + 1);
  };

  // Con la ubicación del cliente, los más cercanos primero; sin ella, por nombre
  const ordenados = useMemo(() => {
    const conDistancia = talleres.map((t) => ({
      ...t,
      distancia: ubicacion ? distanciaKm(ubicacion, t) : null,
    }));
    return conDistancia.sort((a, b) => (ubicacion
      ? a.distancia - b.distancia
      : (a.nombre || "").localeCompare(b.nombre || "", "es")));
  }, [talleres, ubicacion]);

  const elegido = talleres.find((t) => t.id === valor) || null;

  const verEnMapa = (t) => {
    if (!mapa) return;
    mapa.flyTo([t.latitud, t.longitud], Math.max(mapa.getZoom(), 15), { duration: 0.6 });
    marcadores.current[t.id]?.openPopup();
    // En móvil el mapa queda arriba de la lista: se lleva a la vista
    contenedorMapa.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  };

  const elegir = (id) => {
    onElegir(id);
    mapa?.closePopup();
  };

  if (cargando) {
    return <div className="mapa-talleres__estado" role="status">Cargando talleres...</div>;
  }

  if (error) {
    return (
      <div className="mapa-talleres__estado mapa-talleres__estado--error" role="alert">
        <p>{error}</p>
        <p>Puedes enviar la solicitud con asignación automática o intentar cargar el mapa otra vez.</p>
        <button type="button" className="mapa-boton" onClick={reintentar}>Reintentar</button>
      </div>
    );
  }

  return (
    <div className="mapa-talleres">
      <div className="mapa-talleres__mapa" ref={contenedorMapa}>
        <MapContainer
          center={[centroInicial.latitud, centroInicial.longitud]}
          zoom={12}
          scrollWheelZoom
          className="mapa-lienzo"
          ref={setMapa}
        >
          <TileLayer url={TILES_URL} attribution={TILES_ATRIBUCION} />
          <CentrarEnCliente ubicacion={ubicacion} />

          {ubicacion && (
            <CircleMarker
              center={[ubicacion.latitud, ubicacion.longitud]}
              radius={9}
              pathOptions={{ color: "#ffffff", weight: 3, fillColor: "#18487A", fillOpacity: 1 }}
            >
              <Tooltip direction="top" offset={[0, -8]}>Tu ubicación</Tooltip>
            </CircleMarker>
          )}

          {ordenados.map((t) => (
            <Marker
              key={t.id}
              position={[t.latitud, t.longitud]}
              ref={(m) => {
                if (m) marcadores.current[t.id] = m;
                else delete marcadores.current[t.id];
              }}
              icon={t.id === valor ? iconoElegido : iconoNormal}
              zIndexOffset={t.id === valor ? 1000 : 0}
            >
              <Popup>
                <div className="mapa-popup">
                  <strong className="mapa-popup__nombre">{t.nombre}</strong>
                  {t.direccion && <span className="mapa-popup__dato">{t.direccion}{t.ciudad ? `, ${t.ciudad}` : ""}</span>}
                  {t.telefono && <span className="mapa-popup__dato">Tel. {t.telefono}</span>}
                  {t.distancia != null && <span className="mapa-popup__dato">{formatoDistancia(t.distancia)}</span>}
                  {t.id === valor
                    ? <span className="mapa-popup__elegido">Taller elegido</span>
                    : (
                      <button type="button" className="mapa-boton mapa-boton--acento" onClick={() => elegir(t.id)}>
                        Elegir este taller
                      </button>
                    )}
                </div>
              </Popup>
            </Marker>
          ))}
        </MapContainer>
      </div>

      <div className="mapa-talleres__panel">
        <button
          type="button"
          className={`mapa-auto${valor == null ? " mapa-auto--activo" : ""}`}
          aria-pressed={valor == null}
          onClick={() => elegir(null)}
        >
          <span className="mapa-auto__titulo">Asignar automáticamente</span>
          <span className="mapa-auto__detalle">Te asignamos el taller disponible más cercano en tu ciudad.</span>
        </button>

        {ordenados.length === 0 ? (
          <p className="mapa-talleres__vacio">
            No hay talleres disponibles en el mapa por ahora. Usaremos la asignación automática.
          </p>
        ) : (
          <>
            <p className="mapa-talleres__resumen">
              {ordenados.length} {ordenados.length === 1 ? "taller disponible" : "talleres disponibles"}
              {ubicacion ? " · ordenados por cercanía" : ""}
            </p>
            <ul className="mapa-lista">
              {ordenados.map((t) => {
                const activo = t.id === valor;
                return (
                  <li key={t.id} className={`mapa-lista__item${activo ? " mapa-lista__item--elegido" : ""}`}>
                    <button type="button" className="mapa-lista__ver" onClick={() => verEnMapa(t)}>
                      <span className="mapa-lista__nombre">{t.nombre}</span>
                      <span className="mapa-lista__dato">{t.direccion}{t.ciudad ? `, ${t.ciudad}` : ""}</span>
                      {t.distancia != null && <span className="mapa-lista__distancia">{formatoDistancia(t.distancia)}</span>}
                    </button>
                    {activo
                      ? <span className="mapa-lista__marca">Elegido</span>
                      : <button type="button" className="mapa-boton mapa-boton--chico" onClick={() => elegir(t.id)}>Elegir</button>}
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </div>

      <p className="mapa-talleres__seleccion" aria-live="polite">
        {elegido
          ? <>Taller elegido: <strong>{elegido.nombre}</strong>. Si no acepta, te asignamos otro automáticamente.</>
          : "Asignación automática: buscaremos el taller más cercano."}
      </p>
    </div>
  );
}
