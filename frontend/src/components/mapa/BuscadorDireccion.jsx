import { useEffect, useId, useRef, useState } from "react";
import { MIN_CARACTERES_BUSQUEDA, buscarDirecciones, esCancelacion } from "../../services/geoService";

const ESPERA_MS = 400;

/**
 * Buscador de direcciones con autocompletado (Photon). Patrón combobox de ARIA: flechas para recorrer las
 * sugerencias, Enter para elegir y Escape para cerrar. Espera 400 ms desde la última tecla, pide desde
 * 3 caracteres y cancela la búsqueda anterior si llega una nueva.
 * onElegir({ latitud, longitud, titulo, detalle, direccion, barrio, ciudad }).
 * cerca: { latitud, longitud } opcional para priorizar resultados cercanos.
 */
export default function BuscadorDireccion({ id, cerca, onElegir, placeholder = "Busca una dirección o un lugar" }) {
  const propio = useId();
  const base = id || propio;
  const idLista = `${base}-sugerencias`;

  const [texto, setTexto] = useState("");
  // Lo que se busca: se separa del texto para que elegir una sugerencia (que cambia el texto) no busque otra vez
  const [consulta, setConsulta] = useState("");
  const [resultados, setResultados] = useState([]);
  const [estado, setEstado] = useState("inactivo"); // inactivo | buscando | listo | error
  const [abierto, setAbierto] = useState(false);
  const [activo, setActivo] = useState(-1);

  // El sesgo se lee al buscar: mover el pin no debe relanzar la búsqueda
  const cercaRef = useRef(cerca);
  useEffect(() => { cercaRef.current = cerca; }, [cerca]);

  const corta = consulta.trim().length < MIN_CARACTERES_BUSQUEDA;

  useEffect(() => {
    if (corta) return undefined;
    const control = new AbortController();
    const espera = setTimeout(async () => {
      setEstado("buscando");
      try {
        const lista = await buscarDirecciones(consulta, { cerca: cercaRef.current, signal: control.signal });
        setResultados(lista);
        setActivo(-1);
        setEstado("listo");
      } catch (e) {
        if (!esCancelacion(e)) setEstado("error");
      }
    }, ESPERA_MS);
    return () => {
      clearTimeout(espera);
      control.abort();
    };
  }, [consulta, corta]);

  const escribir = (valor) => {
    setTexto(valor);
    setConsulta(valor);
    setAbierto(true);
  };

  const elegir = (r) => {
    setTexto(r.detalle ? `${r.titulo}, ${r.detalle}` : r.titulo);
    setConsulta("");
    setAbierto(false);
    setActivo(-1);
    onElegir(r);
  };

  const limpiar = () => {
    setTexto("");
    setConsulta("");
    setResultados([]);
    setEstado("inactivo");
    setAbierto(false);
  };

  const visibles = corta ? [] : resultados;
  const mostrarLista = abierto && !corta && estado !== "inactivo";

  const tecla = (e) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (!visibles.length) return;
      e.preventDefault();
      setAbierto(true);
      const paso = e.key === "ArrowDown" ? 1 : -1;
      setActivo((i) => (i + paso + visibles.length) % visibles.length);
    } else if (e.key === "Enter") {
      // Nunca envía el formulario que contiene al buscador
      e.preventDefault();
      const elegido = visibles[activo] ?? (visibles.length === 1 ? visibles[0] : null);
      if (mostrarLista && elegido) elegir(elegido);
    } else if (e.key === "Escape") {
      if (mostrarLista) {
        e.preventDefault();
        setAbierto(false);
      } else if (texto) {
        e.preventDefault();
        limpiar();
      }
    }
  };

  let anuncio = "";
  if (!corta && estado === "buscando") anuncio = "Buscando direcciones…";
  else if (!corta && estado === "error") anuncio = "No pudimos buscar en este momento. Intenta de nuevo o marca el punto en el mapa.";
  else if (!corta && estado === "listo") {
    anuncio = visibles.length
      ? `${visibles.length} ${visibles.length === 1 ? "sugerencia" : "sugerencias"}. Usa las flechas para elegir.`
      : "Sin resultados. Prueba con otra dirección o marca el punto en el mapa.";
  }

  return (
    <div className="mapa-buscador">
      <label className="mapa-sr" htmlFor={`${base}-input`}>Buscar dirección</label>
      <div className="mapa-buscador__campo">
        <svg className="mapa-buscador__lupa" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
          <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
        </svg>
        <input
          id={`${base}-input`}
          className="mapa-buscador__input"
          type="text"
          role="combobox"
          autoComplete="off"
          spellCheck={false}
          aria-autocomplete="list"
          aria-expanded={mostrarLista}
          aria-controls={idLista}
          aria-activedescendant={mostrarLista && activo >= 0 ? `${idLista}-${activo}` : undefined}
          aria-describedby={`${base}-ayuda`}
          value={texto}
          placeholder={placeholder}
          onChange={(e) => escribir(e.target.value)}
          onKeyDown={tecla}
          onFocus={() => setAbierto(true)}
          onBlur={() => setAbierto(false)}
        />
        {estado === "buscando" && !corta && <span className="mapa-buscador__girando" aria-hidden="true" />}
        {texto && (
          <button type="button" className="mapa-buscador__limpiar" onClick={limpiar} aria-label="Borrar búsqueda">
            <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false"><path d="M6 6l12 12M18 6 6 18" /></svg>
          </button>
        )}
      </div>
      <span id={`${base}-ayuda`} className="mapa-sr">Escribe al menos {MIN_CARACTERES_BUSQUEDA} caracteres para ver sugerencias.</span>

      <ul id={idLista} role="listbox" aria-label="Sugerencias de dirección" className="mapa-buscador__lista" hidden={!mostrarLista}>
        {visibles.map((r, i) => (
          <li
            key={r.id}
            id={`${idLista}-${i}`}
            role="option"
            aria-selected={i === activo}
            className={`mapa-buscador__opcion${i === activo ? " mapa-buscador__opcion--activa" : ""}`}
            // mousedown en vez de click: el input no pierde el foco antes de elegir
            onMouseDown={(e) => { e.preventDefault(); elegir(r); }}
            onMouseEnter={() => setActivo(i)}
          >
            <span className="mapa-buscador__titulo">{r.titulo}</span>
            {r.detalle && <span className="mapa-buscador__detalle">{r.detalle}</span>}
          </li>
        ))}
        {estado === "listo" && visibles.length === 0 && (
          <li className="mapa-buscador__mensaje" role="presentation">Sin resultados. Prueba con otra dirección o marca el punto en el mapa.</li>
        )}
        {estado === "error" && (
          <li className="mapa-buscador__mensaje mapa-buscador__mensaje--error" role="presentation">
            El buscador no responde. Intenta de nuevo o marca el punto en el mapa.
          </li>
        )}
        {estado === "buscando" && visibles.length === 0 && (
          <li className="mapa-buscador__mensaje" role="presentation">Buscando…</li>
        )}
        <li className="mapa-buscador__fuente" role="presentation">Búsqueda con Photon · datos © OpenStreetMap</li>
      </ul>
      <span className="mapa-sr" aria-live="polite">{abierto ? anuncio : ""}</span>
    </div>
  );
}
