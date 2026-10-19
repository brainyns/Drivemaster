import { useEffect, useMemo, useRef, useState } from "react";
import { MapContainer, Marker, Popup, Tooltip, useMap, useMapEvents } from "react-leaflet";
import Supercluster from "supercluster";
import CapaBase from "./CapaBase";
import BarraUbicacion from "./BarraUbicacion";
import EstadoUbicacion from "./EstadoUbicacion";
import PinArrastrable, { ClicEnMapa } from "./PinArrastrable";
import useUbicacionPin from "./useUbicacionPin";
import { iconoGrupo, iconoMiPunto, iconoTaller, iconoTallerElegido } from "./iconos";
import { conUbicacion, ordenarTalleres, puntosCercanos } from "./ordenTalleres";
import { talleresCercanos, talleresMapa } from "../../services/tallerService";
import { CENTRO_COLOMBIA, ZOOM_CIUDAD, ZOOM_COLOMBIA, centroDeCiudad } from "../../utils/mapa";
import { formatoDistancia } from "../../utils/distancia";
import { formatoDuracion } from "../../utils/geo";
import DatosTallerCliente from "./DatosTallerCliente";
import LogoNegocio from "../ui/LogoNegocio";
import "../../css/mapa.css";

// Espera tras el último cambio de la ubicación antes de pedir los tiempos en carro (el pin se puede seguir moviendo)
const ESPERA_CERCANOS_MS = 800;
// Desde este zoom los talleres se ven sueltos
const ZOOM_SIN_GRUPOS = 15;
const RELLENO = [48, 48];

/** El cliente y sus talleres más cercanos en pantalla. */
function encuadrarCercanos(map, punto, talleres, animar) {
  const puntos = puntosCercanos(punto, talleres);
  if (puntos.length === 1) {
    const zoom = punto.zoom ?? 16;
    if (animar) map.flyTo(puntos[0], zoom, { duration: 0.6 });
    else map.setView(puntos[0], zoom);
    return;
  }
  const opciones = { padding: RELLENO, maxZoom: 16 };
  if (animar) map.flyToBounds(puntos, { ...opciones, duration: 0.6 });
  else map.fitBounds(puntos, opciones);
}

/** Sin ubicación: la ciudad escrita; si no se conoce, todos los talleres; si no hay ninguno con coordenadas, el país. */
function vistaSinUbicacion(map, ciudad, talleres) {
  const c = centroDeCiudad(ciudad);
  if (c) {
    map.setView([c.latitud, c.longitud], ZOOM_CIUDAD);
    return;
  }
  const puntos = talleres.filter(conUbicacion).map((t) => [t.latitud, t.longitud]);
  if (puntos.length) map.fitBounds(puntos, { padding: RELLENO, maxZoom: 13 });
  else map.setView([CENTRO_COLOMBIA.latitud, CENTRO_COLOMBIA.longitud], ZOOM_COLOMBIA);
}

/**
 * Mueve la vista: una vez al saber qué talleres hay (vista inicial), tras una búsqueda o el GPS (el pin y los
 * más cercanos) y, mientras no haya pin, cuando el cliente escribe una ciudad conocida. Al arrastrar o tocar
 * el mapa no se mueve: el cliente está ajustando su punto.
 */
function Encuadre({ listos, talleres, ubicacion, ciudad, encuadre }) {
  const map = useMap();
  const hecho = useRef(false);
  const datos = useRef({ talleres, ubicacion, ciudad });
  useEffect(() => { datos.current = { talleres, ubicacion, ciudad }; });

  useEffect(() => {
    if (!listos || hecho.current) return;
    hecho.current = true;
    const { talleres: lista, ubicacion: u, ciudad: c } = datos.current;
    if (conUbicacion(u)) encuadrarCercanos(map, u, lista, false);
    else vistaSinUbicacion(map, c, lista);
  }, [listos, map]);

  useEffect(() => {
    if (encuadre) encuadrarCercanos(map, encuadre, datos.current.talleres, true);
  }, [encuadre, map]);

  const hayUbicacion = conUbicacion(ubicacion);
  useEffect(() => {
    const c = centroDeCiudad(ciudad);
    if (!hecho.current || hayUbicacion || !c) return undefined;
    const t = setTimeout(() => map.flyTo([c.latitud, c.longitud], ZOOM_CIUDAD, { duration: 0.6 }), 600);
    return () => clearTimeout(t);
  }, [ciudad, hayUbicacion, map]);

  return null;
}

/**
 * Talleres del mapa agrupados por cercanía en pantalla (supercluster). El elegido nunca entra en un grupo.
 * Al tocar un grupo el mapa se acerca hasta separarlo; al tocar un taller se abre su popup y se resalta en la lista.
 */
function TalleresAgrupados({ talleres, valor, marcadores, abrir, onAbierto, onMarcador, popup }) {
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
      eventHandlers={{ click: () => onMarcador(t.id) }}
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
 * El único mapa de Solicitar diagnóstico: el cliente marca dónde está el vehículo (buscador, "Usar mi
 * ubicación", tocar el mapa o arrastrar su pin) y elige taller en el mismo mapa o en la lista de al lado.
 * ubicacion / onUbicacion({ latitud, longitud }): el punto del cliente (el estado vive en la página).
 * onDireccion({ direccion, barrio, ciudad, etiqueta }): la dirección legible del punto, cuando se conoce.
 * valor / onElegir(id | null, nombre | null): el taller elegido; null = asignación automática.
 */
export default function MapaTalleres({ id = "mapa-diagnostico", token, ciudad, ubicacion, onUbicacion, onDireccion, valor, onElegir }) {
  const [talleres, setTalleres] = useState([]);
  const [estadoLista, setEstadoLista] = useState("cargando"); // cargando | listo | error
  const [error, setError] = useState(null);
  const [intento, setIntento] = useState(0);
  const [mapa, setMapa] = useState(null);
  const [abrir, setAbrir] = useState(null);
  const [resaltado, setResaltado] = useState(null); // { id, desde: "mapa" | "lista" }
  const [enCarro, setEnCarro] = useState(null); // { clave, porId: { [id]: { duracionMin, distanciaRutaKm } } }
  const marcadores = useRef({});
  const items = useRef({});
  const lista = useRef(null);
  const contenedorMapa = useRef(null);

  const pin = useUbicacionPin({ value: ubicacion, onChange: onUbicacion, onDireccion });

  const hayUbicacion = conUbicacion(ubicacion);
  const lat = hayUbicacion ? ubicacion.latitud : null;
  const lng = hayUbicacion ? ubicacion.longitud : null;
  const claveUbicacion = hayUbicacion ? `${lat},${lng}` : null;

  // Mientras llegan los talleres: el punto del cliente, su ciudad o el país (Encuadre ajusta después)
  const [vistaInicial] = useState(() => {
    if (hayUbicacion) return { centro: ubicacion, zoom: 15 };
    const c = centroDeCiudad(ciudad);
    return c ? { centro: c, zoom: ZOOM_CIUDAD } : { centro: CENTRO_COLOMBIA, zoom: ZOOM_COLOMBIA };
  });

  useEffect(() => {
    let vivo = true;
    talleresMapa(token)
      .then((l) => { if (vivo) { setTalleres(l || []); setError(null); setEstadoLista("listo"); } })
      .catch((e) => { if (vivo) { setError(e.message); setEstadoLista("error"); } });
    return () => { vivo = false; };
  }, [token, intento]);

  // Tiempo en carro de los más cercanos: una consulta cuando el pin se queda quieto; la anterior se cancela
  useEffect(() => {
    if (lat == null || lng == null) return undefined;
    const control = new AbortController();
    const espera = setTimeout(() => {
      talleresCercanos(token, { latitud: lat, longitud: lng }, { limite: 3, signal: control.signal })
        .then((l) => setEnCarro({
          clave: `${lat},${lng}`,
          porId: Object.fromEntries((l || []).filter((t) => t.duracionMin != null).map((t) => [t.id, t])),
        }))
        // Es un dato extra: sin él se ordena por la distancia en línea recta
        .catch(() => {});
    }, ESPERA_CERCANOS_MS);
    return () => {
      clearTimeout(espera);
      control.abort();
    };
  }, [token, lat, lng]);

  const tiempos = useMemo(
    () => (enCarro && enCarro.clave === claveUbicacion ? enCarro.porId : {}),
    [enCarro, claveUbicacion],
  );
  const ordenados = useMemo(
    () => ordenarTalleres(talleres, lat != null ? { latitud: lat, longitud: lng } : null, tiempos),
    [talleres, lat, lng, tiempos],
  );
  const enMapa = useMemo(() => ordenados.filter((t) => !t.sinUbicacion), [ordenados]);

  // Un marcador tocado se resalta en la lista; si la lista tiene su propio scroll (escritorio), se lleva a la vista.
  // En celular la lista va debajo del mapa y no se mueve la página.
  useEffect(() => {
    if (resaltado?.desde !== "mapa") return;
    const ul = lista.current;
    const li = items.current[resaltado.id];
    if (!ul || !li || ul.scrollHeight <= ul.clientHeight) return;
    const suave = !window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    ul.scrollTo({ top: li.offsetTop - 8, behavior: suave ? "smooth" : "auto" });
  }, [resaltado]);

  const reintentar = () => {
    setEstadoLista("cargando");
    setIntento((n) => n + 1);
  };

  const elegido = talleres.find((t) => t.id === valor) || null;

  const verEnMapa = (t) => {
    setResaltado({ id: t.id, desde: "lista" });
    if (!mapa || t.sinUbicacion) return;
    mapa.closePopup();
    // Al terminar el vuelo el taller ya está suelto (fuera de cualquier grupo) y se abre su popup
    mapa.once("moveend", () => setAbrir(t.id));
    mapa.flyTo([t.latitud, t.longitud], Math.max(mapa.getZoom(), ZOOM_SIN_GRUPOS + 1), { duration: 0.6 });
    // En celular el mapa queda arriba de la lista: se lleva a la vista
    contenedorMapa.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  };

  // El nombre va de segundo argumento para que la página lo muestre (p. ej. en su resumen)
  const elegir = (tallerId) => {
    onElegir(tallerId, tallerId ? talleres.find((t) => t.id === tallerId)?.nombre ?? null : null);
    mapa?.closePopup();
  };

  const distanciaTexto = (t) => {
    if (t.enCarro) return `${formatoDuracion(t.enCarro.duracionMin)} en carro · ${formatoDistancia(t.enCarro.distanciaRutaKm).replace(/^a /, "")}`;
    return t.distancia != null ? formatoDistancia(t.distancia) : null;
  };

  const popup = (t) => {
    const distancia = distanciaTexto(t);
    return (
      <div className="mapa-popup">
        <span className="mapa-popup__cabecera">
          <LogoNegocio url={t.logoUrl} nombre={t.nombre} tamano="mediano" />
          <strong className="mapa-popup__nombre">{t.nombre}</strong>
        </span>
        <DatosTallerCliente taller={t} completo />
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

  let resumen = null;
  if (estadoLista === "listo" && ordenados.length > 0) {
    resumen = `${ordenados.length} ${ordenados.length === 1 ? "taller disponible" : "talleres disponibles"}`
      + (hayUbicacion ? " · ordenados por cercanía a tu punto" : " · marca tu ubicación para ordenarlos por cercanía");
  }

  return (
    <div className="mapa-diagnostico">
      <BarraUbicacion
        id={id}
        cerca={hayUbicacion ? ubicacion : centroDeCiudad(ciudad)}
        onElegirSugerencia={pin.elegirSugerencia}
        onUsarMiUbicacion={pin.usarMiUbicacion}
        ubicando={pin.ubicando}
      />

      <div className="mapa-talleres">
        <div className="mapa-talleres__columna">
          <div className="mapa-talleres__mapa" ref={contenedorMapa}>
            <MapContainer
              center={[vistaInicial.centro.latitud, vistaInicial.centro.longitud]}
              zoom={vistaInicial.zoom}
              scrollWheelZoom
              className="mapa-lienzo"
              ref={setMapa}
            >
              <CapaBase />
              <ClicEnMapa onElegir={pin.moverPin} />
              <Encuadre listos={estadoLista !== "cargando"} talleres={enMapa} ubicacion={ubicacion} ciudad={ciudad}
                encuadre={pin.encuadre} />
              <TalleresAgrupados
                talleres={enMapa}
                valor={valor}
                marcadores={marcadores}
                abrir={abrir}
                onAbierto={() => setAbrir(null)}
                onMarcador={(tallerId) => setResaltado({ id: tallerId, desde: "mapa" })}
                popup={popup}
              />
              {hayUbicacion && (
                <PinArrastrable posicion={ubicacion} onMover={pin.moverPin} icono={iconoMiPunto} titulo="Tu punto">
                  <Tooltip direction="top" permanent className="mapa-tooltip-cliente">Tu punto</Tooltip>
                </PinArrastrable>
              )}
            </MapContainer>
          </div>
          <EstadoUbicacion
            className="mapa-talleres__estado-pin"
            value={ubicacion}
            direccion={pin.direccion}
            aviso={pin.aviso}
            onReintentar={pin.reintentarDireccion}
            ayuda="Opcional: busca la dirección, usa tu ubicación o toca el mapa para marcar dónde está el vehículo. Así ordenamos los talleres por cercanía."
          />
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

          {estadoLista === "cargando" && (
            <p className="mapa-talleres__resumen" role="status"><span className="mapa-cargando" aria-hidden="true" /> Cargando talleres…</p>
          )}
          {estadoLista === "error" && (
            <div className="mapa-talleres__estado mapa-talleres__estado--error" role="alert">
              <p>{error}</p>
              <p>Puedes enviar la solicitud con asignación automática o intentar cargar los talleres otra vez.</p>
              <button type="button" className="mapa-boton" onClick={reintentar}>Reintentar</button>
            </div>
          )}
          {estadoLista === "listo" && ordenados.length === 0 && (
            <p className="mapa-talleres__vacio">
              No hay talleres disponibles por ahora. Usaremos la asignación automática.
            </p>
          )}
          <p className="mapa-talleres__resumen" aria-live="polite">{resumen}</p>

          {ordenados.length > 0 && (
            <ul className="mapa-lista" ref={lista} aria-label="Talleres disponibles">
              {ordenados.map((t, i) => {
                const activo = t.id === valor;
                const distancia = distanciaTexto(t);
                const clases = ["mapa-lista__item", activo && "mapa-lista__item--elegido",
                  resaltado?.id === t.id && "mapa-lista__item--resaltado"].filter(Boolean).join(" ");
                return (
                  <li key={t.id} className={clases} ref={(el) => { if (el) items.current[t.id] = el; else delete items.current[t.id]; }}>
                    <LogoNegocio url={t.logoUrl} nombre={t.nombre} className="mapa-lista__logo" />
                    <button type="button" className="mapa-lista__ver" onClick={() => verEnMapa(t)}
                      aria-label={t.sinUbicacion ? t.nombre : `Ver ${t.nombre} en el mapa`}>
                      <span className="mapa-lista__nombre">
                        {t.nombre}
                        {hayUbicacion && i === 0 && !t.sinUbicacion && <span className="mapa-lista__insignia">Más cercano</span>}
                      </span>
                      <span className="mapa-lista__dato">{t.direccion}{t.ciudad ? `, ${t.ciudad}` : ""}</span>
                      <DatosTallerCliente taller={t} />
                      {distancia && <span className="mapa-lista__distancia">{distancia}</span>}
                      {t.sinUbicacion && <span className="mapa-lista__nota">Sin ubicación en el mapa</span>}
                    </button>
                    {activo
                      ? <span className="mapa-lista__marca">Elegido</span>
                      : <button type="button" className="mapa-boton mapa-boton--chico" onClick={() => elegir(t.id)}
                          aria-label={`Elegir ${t.nombre}`}>Elegir</button>}
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <p className="mapa-talleres__seleccion" aria-live="polite">
          {elegido
            ? <>Taller elegido: <strong>{elegido.nombre}</strong>. Si no acepta, te asignamos otro automáticamente.</>
            : "Asignación automática: buscaremos el taller más cercano."}
        </p>
      </div>
    </div>
  );
}
