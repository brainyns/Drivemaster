import { useId, useMemo, useState } from "react";
import Campo from "./Campo";
import ImagenProducto from "../marketplace/ImagenProducto";
import Icon from "../public/Icon";
import { buscarProductos } from "../../pages/tienda/inventarioTienda";

/**
 * Buscador de un producto del catálogo con autocompletado (combobox accesible): se escribe el nombre, el código SKU o
 * la referencia y cada opción muestra la foto, el nombre, el código y el stock actual. Flechas para moverse, Enter para
 * elegir y Escape para cerrar. Elegido, se reemplaza por una tarjeta con la foto y el stock, y "Cambiar" lo suelta.
 */
export default function BuscadorProducto({ id, label = "Producto", productos, elegido, onElegir, error, autoFocus = false }) {
  const [texto, setTexto] = useState("");
  const [abierto, setAbierto] = useState(false);
  const [activo, setActivo] = useState(0);
  const idLista = `${useId()}-lista`;
  const opciones = useMemo(() => buscarProductos(productos, texto), [productos, texto]);
  const mostrar = abierto && texto.trim() !== "";

  const elegir = (p) => {
    onElegir(p);
    setTexto("");
    setAbierto(false);
  };

  const alTeclear = (e) => {
    if (e.key === "ArrowDown") { e.preventDefault(); setAbierto(true); setActivo((i) => Math.min(i + 1, opciones.length - 1)); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setActivo((i) => Math.max(i - 1, 0)); }
    else if (e.key === "Enter" && mostrar && opciones[activo]) { e.preventDefault(); elegir(opciones[activo]); }
    else if (e.key === "Escape") setAbierto(false);
  };

  if (elegido) {
    return (
      <div className="ui-field">
        <span className="ui-field__label">{label}</span>
        <div className="ui-producto-elegido">
          <ImagenProducto src={elegido.imagenUrl} className="ui-producto-elegido__imagen" tamanioIcono={22} />
          <span className="ui-producto-elegido__texto">
            <strong>{elegido.nombre}</strong>
            <span>{elegido.codigo ? `Código ${elegido.codigo}` : "Sin código"}</span>
          </span>
          <span className="ui-producto-elegido__stock">
            <span>Stock actual</span>
            <strong>{elegido.stockActual ?? 0}</strong>
          </span>
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => onElegir(null)}
            aria-label={`Cambiar el producto ${elegido.nombre}`}>
            Cambiar
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="ui-combo">
      <Campo id={id} label={label} error={error} ayuda="Escribe el nombre, el código SKU o la referencia.">
        <input className="ui-field__input" type="search" autoComplete="off" value={texto} autoFocus={autoFocus}
          role="combobox" aria-autocomplete="list" aria-expanded={mostrar} aria-controls={idLista}
          aria-activedescendant={mostrar && opciones[activo] ? `${idLista}-${activo}` : undefined}
          placeholder="Ej. pastillas o KNT-BRK-001"
          onChange={(e) => { setTexto(e.target.value); setAbierto(true); setActivo(0); }}
          onFocus={() => setAbierto(true)} onBlur={() => setAbierto(false)} onKeyDown={alTeclear} />
      </Campo>
      {mostrar && (
        <ul id={idLista} role="listbox" className="ui-combo__lista" aria-label="Productos que coinciden">
          {opciones.length === 0 ? (
            <li className="ui-combo__vacio" role="presentation">Ningún producto coincide con “{texto.trim()}”.</li>
          ) : opciones.map((p, i) => (
            <li key={p.id} id={`${idLista}-${i}`} role="option" aria-selected={i === activo}
              className={`ui-combo__opcion${i === activo ? " ui-combo__opcion--activa" : ""}`}
              // mousedown en vez de click: elige antes de que el campo pierda el foco y cierre la lista
              onMouseDown={(e) => { e.preventDefault(); elegir(p); }} onMouseEnter={() => setActivo(i)}>
              <ImagenProducto src={p.imagenUrl} className="ui-combo__imagen" tamanioIcono={16} />
              <span className="ui-combo__texto">
                <strong>{p.nombre}</strong>
                <span>{p.codigo || "Sin código"}</span>
              </span>
              <span className="ui-combo__stock">Stock {p.stockActual ?? 0}</span>
            </li>
          ))}
        </ul>
      )}
      {!mostrar && texto.trim() === "" && productos.length === 0 && (
        <p className="ui-combo__nota"><Icon name="box" size={14} /> Aún no tienes productos en tu catálogo.</p>
      )}
    </div>
  );
}
