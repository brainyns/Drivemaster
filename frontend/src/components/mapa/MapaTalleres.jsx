import { useEffect, useMemo, useRef, useState } from "react";
import { MapContainer, Marker, Popup, Tooltip, useMap, useMapEvents } from "react-leaflet";
import Supercluster from "supercluster";
import CapaBase from "./CapaBase";
import { iconoCliente, iconoGrupo, iconoTaller, iconoTallerElegido } from "./iconos";
import { talleresCercanos, talleresMapa } from "../../services/tallerService";
import { centroDeCiudad } from "../../utils/mapa";
import { distanciaKm, formatoDistancia } from "../../utils/distancia";
import { formatoDuracion } from "../../utils/geo";
import "../../css/mapa.css";

// Espera tras el último cambio de la ubicación antes de pedir los tiempos en carro (el pin se puede seguir moviendo)
const ESPERA_CERCANOS_MS = 800;
// Desde este zoom los talleres se ven sueltos
const ZOOM_SIN_GRUPOS = 15;

/** Centra el mapa en la ubicación del cliente cuando cambia. */
function CentrarEnCliente({ latitud, longitud }) {
  const map = useMap();
  useEffect(() => {
    if (latitud != null && longitud != null) map.flyTo([latitud, longitud], 13, { duration: 0.6 });
  }, [latitud, longitud, map]);
  return null;
}

/**
 * Talleres del mapa agrupados por cercanía en pantalla (supercluster). El elegido nunca entra en un grupo.
 * Al tocar un grupo el mapa se acerca hasta separarlo.
 */
function TalleresAgrupados({ talleres, valor, marcadores, abrir, onAbierto, popup }) {
  const map = useMap();
  const [vista, setVista] = useState(() => ({ zoom: map.getZoom(), limites: map.getBounds() }));
  useMapEvents({ moveend: () => setVista({ zoom: map.getZoom(), limites: map.getBounds() }) });

  const agrupables = useMemo(() => talleres.filter((t) => t.id !== valor), [talleres, valor]);
  const indice = useMemo(() => {
    const i = new Supercluster({ radius: 56, maxZoom: ZOOM_SIN_GRUPOS - 1 });
    i.load(agrupables.map((t) => ({
      type: "Feature",
      properties: { id: t.id },
      geometry: { type: "Point", coordinates: [t.longitud, t.latitud] },
    })));
    return i;
  }, [agrupables]);
  const porId = useMemo(() => new Map(talleres.map((t) => [t.id, t])), [talleres]);

  const l = vista.limites;
  const puntos = indice.getClusters([l.getWest(), l.getSouth(), l.getEast(), l.getNorth()], Math.floor(vista.zoom));
  const elegido = valor ? porId.get(valor) : null;

  // Abre el popup pedido desde la lista en cuanto su marcador está en el mapa
  useEffect(() => {
    if (!abrir) return;
    const m = marcadores.current[abrir];
    if (m) {
      m.openPopup();
      onAbierto();
    }
  });

  const marcador = (t) => (
    <Marker
      key={t.id}
      position={[t.latitud, t.longitud]}
      icon={t.id === valor ? iconoTallerElegido : iconoTaller}
      title={t.nombre}
      zIndexOffset={t.id === valor ? 1000 : 0}
      ref={(m) => {
        if (m) marcadores.current[t.id] = m;
        else delete marcadores.current[t.id];
      }}
    >
      <Popup>{popup(t)}</Popup>
    </Marker>
  );

  return (
    <>
      {puntos.map((p) => {
        const [lng, lat] = p.geometry.coordinates;
        if (!p.properties.cluster) {
          const t = porId.get(p.properties.id);
          return t ? marcador(t) : null;
        }
        const n = p.properties.point_count;
        return (
          <Marker
            key={`grupo-${p.id}`}
            position={[lat, lng]}
            icon={iconoGrupo(n)}
            title={`${n} talleres en esta zona. Acercar`}
            eventHandlers={{
              click: () => map.flyTo([lat, lng], Math.min(indice.getClusterExpansionZoom(p.id), 18), { duration: 0.5 }),
            }}
          />
        );
      })}
      {elegido && marcador(elegido)}
    </>
  );
}

/**
 * Mapa y lista de talleres para que el cliente elija uno al pedir un diagnóstico.
 * valor: id del taller elegido, o null = asignación automática. onElegir(id | null).
 * ubicacion: { latitud, longitud } del cliente (opcional); ordena por distancia y, para los tres más
 * cercanos, muestra el tiempo en carro.
 */
export default function MapaTalleres({ token, valor, onElegir, ubicacion, ciudad }) {
  const [talleres, setTalleres] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [intento, setIntento] = useState(0);
  const [mapa, setMapa] = useState(null);
  const [centroInicial] = useState(() => (ubicacion?.latitud != null ? ubicacion : centroDeCiudad(ciudad)));
  const [abrir, setAbrir] = useState(null);
  const [enCarro, setEnCarro] = useState(null); // { clave, porId: { [id]: { duracionMin, distanciaRutaKm } } }
  const marcadores = useRef({});
  const contenedorMapa = useRef(null);

  const lat = ubicacion?.latitud ?? null;
  const lng = ubicacion?.longitud ?? null;
  const claveUbicacion = lat != null && lng != null ? `${lat},${lng}` : null;

  useEffect(() => {
    let vivo = true;
    talleresMapa(token)
      .then((lista) => { if (vivo) { setTalleres(lista || []); setError(null); } })
      .catch((e) => { if (vivo) setError(e.message); })
      .finally(() => { if (vivo) setCargando(false); });
    return () => { vivo = false; };
  }, [token, intento]);

  // Tiempo en carro de los más cercanos: una consulta cuando la ubicación se queda quieta; la anterior se cancela
  useEffect(() => {
    if (lat == null || lng == null) return undefined;
    const control = new AbortController();
    const espera = setTimeout(() => {
      talleresCercanos(token, { latitud: lat, longitud: lng }, { limite: 3, signal: control.signal })
        .then((lista) => setEnCarro({
          clave: `${lat},${lng}`,
          porId: Object.fromEntries((lista || []).filter((t) => t.duracionMin != null).map((t) => [t.id, t])),
        }))
        // Es un dato extra: sin él se muestra la distancia en línea recta
        .catch(() => {});
    }, ESPERA_CERCANOS_MS);
    return () => {
      clearTimeout(espera);
      control.abort();
    };
  }, [token, lat, lng]);

  const tiempos = enCarro && enCarro.clave === claveUbicacion ? enCarro.porId : {};

  const reintentar = () => {
    setCargando(true);
    setIntento((n) => n + 1);
  };

  // Con la ubicación del cliente, los más cercanos primero; sin ella, por nombre
  const ordenados = useMemo(() => {
    const conDistancia = talleres.map((t) => ({
      ...t,
      distancia: lat != null && lng != null ? distanciaKm({ latitud: lat, longitud: lng }, t) : null,
    }));
    return conDistancia.sort((a, b) => (lat != null
      ? a.distancia - b.distancia
      : (a.nombre || "").localeCompare(b.nombre || "", "es")));
  }, [talleres, lat, lng]);

  const elegido = talleres.find((t) => t.id === valor) || null;

  const verEnMapa = (t) => {
    if (!mapa) return;
    mapa.closePopup();
    // Al terminar el vuelo el taller ya está suelto (fuera de cualquier grupo) y se abre su popup
    mapa.once("moveend", () => setAbrir(t.id));
    mapa.flyTo([t.latitud, t.longitud], Math.max(mapa.getZoom(), ZOOM_SIN_GRUPOS + 1), { duration: 0.6 });
    // En móvil el mapa queda arriba de la lista: se lleva a la vista
    contenedorMapa.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  };

  const elegir = (id) => {
    onElegir(id);
    mapa?.closePopup();
  };

  const distanciaTexto = (t) => {
    const carro = tiempos[t.id];
    if (carro) return `${formatoDuracion(carro.duracionMin)} en carro · ${formatoDistancia(carro.distanciaRutaKm).replace(/^a /, "")}`;
    return t.distancia != null ? formatoDistancia(t.distancia) : null;
  };

  const popup = (t) => {
    const distancia = distanciaTexto(t);
    return (
      <div className="mapa-popup">
        <strong className="mapa-popup__nombre">{t.nombre}</strong>
        {t.direccion && <span className="mapa-popup__dato">{t.direccion}{t.ciudad ? `, ${t.ciudad}` : ""}</span>}
        {t.telefono && <span className="mapa-popup__dato">Tel. {t.telefono}</span>}
        {distancia && <span className="mapa-popup__distancia">{distancia}</span>}
        {t.id === valor
          ? <span className="mapa-popup__elegido">Taller elegido</span>
          : (
            <button type="button" className="mapa-boton mapa-boton--acento" onClick={() => elegir(t.id)}>
              Elegir este taller
            </button>
          )}
      </div>
    );
  };

  if (cargando) {
    return (
      <div className="mapa-talleres__estado" role="status">
        <span className="mapa-cargando" aria-hidden="true" /> Cargando talleres…
      </div>
    );
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
          <CapaBase />
          <CentrarEnCliente latitud={lat} longitud={lng} />

          {lat != null && lng != null && (
            <Marker position={[lat, lng]} icon={iconoCliente} keyboard={false} interactive={false} zIndexOffset={500}>
              <Tooltip direction="top" permanent={false}>Tu ubicación</Tooltip>
            </Marker>
          )}

          <TalleresAgrupados
            talleres={ordenados}
            valor={valor}
            marcadores={marcadores}
            abrir={abrir}
            onAbierto={() => setAbrir(null)}
            popup={popup}
          />
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
              {lat != null ? " · ordenados por cercanía" : " · marca tu ubicación para verlos por cercanía"}
            </p>
            <ul className="mapa-lista">
              {ordenados.map((t, i) => {
                const activo = t.id === valor;
                const distancia = distanciaTexto(t);
                return (
                  <li key={t.id} className={`mapa-lista__item${activo ? " mapa-lista__item--elegido" : ""}`}>
                    <button type="button" className="mapa-lista__ver" onClick={() => verEnMapa(t)}
                      aria-label={`Ver ${t.nombre} en el mapa`}>
                      <span className="mapa-lista__nombre">
                        {t.nombre}
                        {lat != null && i === 0 && <span className="mapa-lista__insignia">Más cercano</span>}
                      </span>
                      <span className="mapa-lista__dato">{t.direccion}{t.ciudad ? `, ${t.ciudad}` : ""}</span>
                      {distancia && <span className="mapa-lista__distancia">{distancia}</span>}
                    </button>
                    {activo
                      ? <span className="mapa-lista__marca">Elegido</span>
                      : <button type="button" className="mapa-boton mapa-boton--chico" onClick={() => elegir(t.id)}
                          aria-label={`Elegir ${t.nombre}`}>Elegir</button>}
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
