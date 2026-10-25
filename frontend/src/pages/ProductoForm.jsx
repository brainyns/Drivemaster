import { useEffect, useRef, useState } from "react";
import { crearProducto, actualizarProducto, obtenerProducto, listarProductos, listarProductosMiTienda, subirImagenProducto } from "../services/productoService";
import { sugerirProducto } from "../services/informesTiendaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import SeccionEditable from "../components/ui/SeccionEditable";
import Campo from "../components/ui/Campo";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import Esqueleto from "../components/ui/Esqueleto";
import BarraAccionMovil from "../components/ui/BarraAccionMovil";
import ImagenProducto from "../components/marketplace/ImagenProducto";
import Icon from "../components/public/Icon";
import {
  ANO_ACTUAL, CATEGORIAS, COMPAT_VACIA, MARCAS_REPUESTO, MARCAS_VEHICULO, MARCAS_VEHICULO_LISTA, TIPOS_IMAGEN,
  campoVacio, compatDeLista, errorImagen, margen as calcularMargen, mismaCompat, precioConIva, validarFormulario,
} from "./tienda/reglasProducto";
import "../css/producto-form.css";
import { etiquetaCategoria } from "../utils/categorias";
import { formatoCop } from "../utils/formato";

/**
 * Sugerencias de la IA para el producto (solo TIENDA): categoría de la lista y compatibilidades "por confirmar".
 * Nada se guarda ni se cambia en el formulario hasta que la tienda pulsa "Aplicar" o "Agregar".
 */
function SugerenciasIA({ form, setForm, token }) {
  const [estado, setEstado] = useState({ cargando: false, error: null, datos: null });
  const puedePedir = form.nombre.trim().length >= 3;

  const pedir = async () => {
    setEstado({ cargando: true, error: null, datos: null });
    try {
      const datos = await sugerirProducto(token, form, CATEGORIAS);
      setEstado({ cargando: false, error: null, datos });
    } catch (e) {
      setEstado({ cargando: false, error: e.message, datos: null });
    }
  };

  const agregar = (compats) => setForm((prev) => {
    // Las filas vacías del formulario se reemplazan; las que ya están no se repiten.
    const llenas = prev.modelosCompatibles.filter((c) => !campoVacio(c.marca));
    const nuevas = compats.filter((c) => !llenas.some((x) => mismaCompat(x, c)));
    return { ...prev, compatibilidadUniversal: false, modelosCompatibles: [...llenas, ...nuevas] };
  });

  const { cargando, error, datos } = estado;
  const sugeridas = (datos?.compatibilidades || []).map((c) => ({ original: c, deLista: compatDeLista(c) }));
  const agregables = sugeridas.map((s) => s.deLista).filter(Boolean)
    .filter((c) => !form.modelosCompatibles.some((x) => mismaCompat(x, c)));

  return (
    <SeccionEditable id="pfm-ia" icono="bolt" titulo="Sugerencias de la IA"
      descripcion={puedePedir ? "Propone la categoría y los vehículos compatibles. Nada se guarda sin que lo apliques."
        : "Escribe el nombre del producto (y si quieres la marca y la referencia) para pedir sugerencias."}>
      <div aria-live="polite" className="pfm-ia">
        <button type="button" className="ui-btn ui-btn--secundario pfm-ia__pedir" onClick={pedir} disabled={!puedePedir || cargando} aria-busy={cargando}>
          {!cargando && <Icon name="bolt" size={16} />}
          {cargando ? "Pensando…" : datos ? "Sugerir de nuevo" : "Sugerir"}
        </button>
        {error && <Alerta onCerrar={() => setEstado({ cargando: false, error: null, datos: null })}>{error}</Alerta>}

        {datos && (
          <>
            <div className="pfm-ia__bloque">
              <p className="pfm-ia__titulo"><ChipEstado tono="aviso">Por confirmar</ChipEstado> Categoría</p>
              {datos.categoria ? (
                <div className="pfm-ia__fila">
                  <span><strong>{etiquetaCategoria(datos.categoria)}</strong> (de tu lista de categorías)</span>
                  {form.categoria === datos.categoria
                    ? <ChipEstado tono="ok">Aplicada</ChipEstado>
                    : <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => setForm((p) => ({ ...p, categoria: datos.categoria }))}>Aplicar</button>}
                </div>
              ) : <p className="pfm-ia__nota">No encontró una categoría de tu lista que encaje. Elígela a mano.</p>}
            </div>

            <div className="pfm-ia__bloque">
              <p className="pfm-ia__titulo"><ChipEstado tono="aviso">Por confirmar</ChipEstado> Vehículos compatibles</p>
              <Alerta tono="aviso">
                Revisa cada compatibilidad con el catálogo del fabricante: una equivocada hace que el cliente compre la pieza que no es.
              </Alerta>
              {sugeridas.length === 0 ? (
                <p className="pfm-ia__nota">No propuso compatibilidades seguras para este producto.</p>
              ) : (
                <ul className="pfm-ia__lista">
                  {sugeridas.map(({ original: c, deLista }, i) => {
                    const yaEsta = deLista && form.modelosCompatibles.some((x) => mismaCompat(x, deLista));
                    return (
                      <li key={i} className="pfm-ia__fila">
                        <span>{c.marca} {c.modelo} · {c.anoDesde}–{c.anoHasta}{c.motor ? ` · motor ${c.motor}` : ""}
                          {!deLista && <em className="pfm-ia__fuera"> No está en la lista del formulario: agrégalo a mano si es correcto.</em>}
                        </span>
                        {deLista && (yaEsta
                          ? <ChipEstado tono="ok">Agregada</ChipEstado>
                          : <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => agregar([deLista])}>Agregar</button>)}
                      </li>
                    );
                  })}
                </ul>
              )}
              {agregables.length > 1 && (
                <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => agregar(agregables)}>
                  Agregar las {agregables.length}
                </button>
              )}
            </div>
            {datos.nota && <p className="pfm-ia__nota">{datos.nota}</p>}
          </>
        )}
      </div>
    </SeccionEditable>
  );
}

/** Campo de dinero con el signo $ delante. */
function Dinero(props) {
  return (
    <span className="pfm-prefijo">
      <span aria-hidden="true">$</span>
      <input {...props} className="ui-field__input" type="text" inputMode="decimal" placeholder="0" />
    </span>
  );
}

function ProductoForm({ id, onVolver, token, esTienda = false, ivaRate = null }) {
  const esEdicion = Boolean(id);

  const [form, setForm] = useState({
    codigo: "", nombre: "", categoria: "", marca: "",
    referencia: "", ubicacion: "", diasEntregaEncargo: "",
    precioCompra: "", precioVenta: "",
    stockActual: "", stockMinimo: "",
    tipo: "STOCK",
    compatibilidadUniversal: false,
    modelosCompatibles: [COMPAT_VACIA],
  });

  const [productosExistentes, setProductosExistentes] = useState([]);
  const [cargandoProducto, setCargandoProducto] = useState(esEdicion);
  const [guardando, setGuardando] = useState(false);
  const [serverError, setServerError] = useState(null);
  const [submitted, setSubmitted] = useState(false);
  const [imagenFile, setImagenFile] = useState(null);
  const [imagenPreview, setImagenPreview] = useState(null);
  const [errorFoto, setErrorFoto] = useState(null);
  const [subiendoImagen, setSubiendoImagen] = useState(false);
  const fileInputRef = useRef(null);
  const resumenRef = useRef(null);

  // El código SKU es único dentro de cada tienda: una tienda valida contra su propio catálogo.
  useEffect(() => {
    (esTienda ? listarProductosMiTienda(token) : listarProductos(token))
      .then(setProductosExistentes)
      .catch(() => {});
  }, [token, esTienda]);

  useEffect(() => {
    if (!esEdicion) return;
    obtenerProducto(id, token)
      .then((data) => {
        setForm({
          ...data,
          referencia: data.referencia || "",
          ubicacion: data.ubicacion || "",
          diasEntregaEncargo: data.diasEntregaEncargo ?? "",
          compatibilidadUniversal: !!data.compatibilidadUniversal,
          modelosCompatibles: data.modelosCompatibles?.length
            ? data.modelosCompatibles.map((c) => ({ ...c, motor: c.motor || "" }))
            : [COMPAT_VACIA],
        });
        if (data.imagenUrl) setImagenPreview(data.imagenUrl);
      })
      .catch((e) => setServerError(e.message))
      .finally(() => setCargandoProducto(false));
  }, [id, token, esEdicion]);

  // Después del primer intento de guardar, los errores se recalculan en cada cambio (la validación es pura)
  const errores = submitted ? validarFormulario(form, productosExistentes, esEdicion, id) : {};
  const actualizar = (cambio) => setForm((prev) => (typeof cambio === "function" ? cambio(prev) : { ...prev, ...cambio }));

  const handleChange = (e) => {
    const { name, value } = e.target;
    actualizar({ [name]: value });
  };

  const handleImagenChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const err = errorImagen(file);
    setErrorFoto(err);
    if (err) { e.target.value = ""; return; }
    setImagenFile(file);
    setImagenPreview(URL.createObjectURL(file));
  };

  const eliminarImagenSeleccionada = () => {
    setImagenFile(null);
    setImagenPreview(null);
    setErrorFoto(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleCompatibilidadChange = (index, e) => {
    const { name, value } = e.target;
    actualizar((prev) => ({
      ...prev,
      modelosCompatibles: prev.modelosCompatibles.map((c, i) =>
        i === index ? { ...c, [name]: value, ...(name === "marca" ? { modelo: "" } : {}) } : c),
    }));
  };

  const agregarCompatibilidad = () => actualizar((prev) => ({ ...prev, modelosCompatibles: [...prev.modelosCompatibles, COMPAT_VACIA] }));
  const eliminarCompatibilidad = (index) => actualizar((prev) => ({
    ...prev, modelosCompatibles: prev.modelosCompatibles.filter((_, i) => i !== index),
  }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitted(true);
    const e2 = validarFormulario(form, productosExistentes, esEdicion, id);
    if (Object.keys(e2).length > 0) {
      // El resumen se anuncia y el foco va al primer campo con error
      requestAnimationFrame(() => document.querySelector(".pfm-root [aria-invalid='true']")?.focus());
      return;
    }

    setGuardando(true);
    setServerError(null);
    try {
      const payload = {
        ...form,
        nombre: form.nombre.trim(),
        codigo: form.codigo.trim().toUpperCase(),
        referencia: form.referencia.trim() || null,
        ubicacion: form.ubicacion.trim() || null,
        // Solo aplica a productos por encargo (tiempo estimado que ve el taller al cotizar).
        diasEntregaEncargo: form.tipo === "ENCARGO" && String(form.diasEntregaEncargo).trim() !== ""
          ? Number(form.diasEntregaEncargo) : null,
        precioCompra: Number(form.precioCompra),
        precioVenta: Number(form.precioVenta),
        stockActual: Number(form.stockActual),
        tipo: form.tipo,
        stockMinimo: Number(form.stockMinimo),
        compatibilidadUniversal: !!form.compatibilidadUniversal,
        // Universal: sirve a cualquier vehículo, no se guardan modelos.
        modelosCompatibles: form.compatibilidadUniversal ? [] : form.modelosCompatibles.map((c) => ({
          ...c,
          anoDesde: Number(c.anoDesde),
          anoHasta: Number(c.anoHasta),
          motor: c.motor?.trim() || null,
        })),
      };
      let productoId = id;
      if (esEdicion) {
        await actualizarProducto(id, payload, token);
      } else {
        const mov = await crearProducto(payload, token);
        productoId = mov.productoId;
      }
      if (imagenFile) {
        setSubiendoImagen(true);
        await subirImagenProducto(productoId, imagenFile, token);
      }
      onVolver();
    } catch (err) {
      setServerError(err.message);
      resumenRef.current?.scrollIntoView({ block: "center" });
    } finally {
      setSubiendoImagen(false);
      setGuardando(false);
    }
  };

  const margen = calcularMargen(form.precioCompra, form.precioVenta);
  const paraCliente = precioConIva(form.precioVenta, ivaRate);
  const compatErrores = errores.compatibilidad || [];
  const cantidadErrores = Object.keys(errores).filter((k) => k !== "compatibilidad").length
    + compatErrores.filter(Boolean).reduce((s, e) => s + Object.keys(e).length, 0);
  const textoGuardar = guardando ? (subiendoImagen ? "Subiendo la foto…" : "Guardando…") : esEdicion ? "Guardar cambios" : "Guardar producto";

  const titulo = esEdicion ? "Editar producto" : "Nuevo producto";
  if (cargandoProducto) {
    return (
      <div className="ss-panel--tienda pfm-root">
        <EncabezadoPagina titulo={titulo} onVolver={onVolver} textoVolver="Productos" />
        <Esqueleto variante="formulario" cantidad={5} texto="Cargando el producto…" />
      </div>
    );
  }

  return (
    <div className="ss-panel--tienda pfm-root">
      <EncabezadoPagina titulo={esEdicion && form.nombre ? form.nombre : titulo} onVolver={onVolver} textoVolver="Productos"
        descripcion={esEdicion
          ? "Los cambios se ven en el catálogo apenas guardas."
          : "Con foto, precio y vehículos compatibles, los clientes y talleres encuentran el repuesto correcto."} />

      <div ref={resumenRef}>
        {serverError && <Alerta className="pfm-alerta" onCerrar={() => setServerError(null)}>{serverError}</Alerta>}
        {submitted && cantidadErrores > 0 && (
          <Alerta className="pfm-alerta">
            Revisa {cantidadErrores === 1 ? "el campo marcado" : `los ${cantidadErrores} campos marcados`} antes de guardar.
          </Alerta>
        )}
      </div>

      <form id="pfm-form" onSubmit={handleSubmit} noValidate className="pfm-layout">
        <div className="pfm-columna">
          <SeccionEditable id="pfm-general" icono="box" titulo="Información general"
            descripcion="Cómo se identifica el repuesto en tu inventario y en el catálogo.">
            <div className="ui-form">
              <Campo id="pfm-nombre" label="Nombre del producto" error={errores.nombre}>
                <input className="ui-field__input" name="nombre" value={form.nombre} onChange={handleChange}
                  placeholder="Ej. Pastillas de freno cerámicas Brembo" />
              </Campo>
              <div className="ui-form__campos ui-form__campos--2">
                <Campo id="pfm-categoria" label="Categoría" error={errores.categoria}>
                  <select className="ui-field__input" name="categoria" value={form.categoria} onChange={handleChange}>
                    <option value="">Elegir categoría</option>
                    {CATEGORIAS.map((c) => <option key={c} value={c}>{etiquetaCategoria(c)}</option>)}
                  </select>
                </Campo>
                <Campo id="pfm-codigo" label="Código SKU" error={errores.codigo} ayuda="Único dentro de tu tienda.">
                  <input className="ui-field__input" name="codigo" value={form.codigo} onChange={handleChange}
                    placeholder="Ej. KNT-BRK-001" autoCapitalize="characters" />
                </Campo>
                <Campo id="pfm-marca" label="Marca del repuesto" error={errores.marca}>
                  <select className="ui-field__input" name="marca" value={form.marca} onChange={handleChange}>
                    <option value="">Elegir marca</option>
                    {MARCAS_REPUESTO.map((m) => <option key={m} value={m}>{m}</option>)}
                  </select>
                </Campo>
                <Campo id="pfm-referencia" label="Referencia del fabricante" opcional>
                  <input className="ui-field__input" name="referencia" value={form.referencia} onChange={handleChange} placeholder="Ej. P 85 020" />
                </Campo>
                <Campo id="pfm-ubicacion" label="Ubicación en bodega" opcional>
                  <input className="ui-field__input" name="ubicacion" value={form.ubicacion} onChange={handleChange} placeholder="Ej. Estante B-3" />
                </Campo>
              </div>
            </div>
          </SeccionEditable>

          {esTienda && <SugerenciasIA form={form} setForm={setForm} token={token} />}

          <SeccionEditable id="pfm-tipo" icono="clock" titulo="Disponibilidad"
            descripcion="Si lo tienes en bodega o lo consigues cuando te lo piden.">
            <div className="ui-form">
              <fieldset className="pfm-opciones">
                <legend className="ui-sr">Tipo de producto</legend>
                {[["STOCK", "En stock", "Se compra y se despacha de inmediato."],
                  ["ENCARGO", "Por encargo", "Lo consigues cuando te lo piden; el taller ve el tiempo de entrega."]].map(([valor, texto, ayuda]) => (
                  <label key={valor} className={`ui-opcion pfm-opcion${form.tipo === valor ? " ui-opcion--elegida" : ""}`}>
                    <input type="radio" className="ui-sr" name="tipo" value={valor} checked={form.tipo === valor} onChange={handleChange} />
                    <span><strong>{texto}</strong><span>{ayuda}</span></span>
                  </label>
                ))}
              </fieldset>
              {form.tipo === "ENCARGO" && (
                <Campo id="pfm-dias" label="Días estimados de entrega" opcional error={errores.diasEntregaEncargo} ayuda="Entre 1 y 120 días.">
                  <input className="ui-field__input ui-form__corto" type="number" name="diasEntregaEncargo" min="1" max="120"
                    value={form.diasEntregaEncargo} onChange={handleChange} placeholder="Ej. 5" />
                </Campo>
              )}
            </div>
          </SeccionEditable>

          <SeccionEditable id="pfm-compatibles" icono="car" titulo="Vehículos compatibles"
            descripcion="Con esto el catálogo le dice al cliente si el repuesto le sirve a su vehículo.">
            <div className="ui-form">
              <label className="pfm-universal">
                <input type="checkbox" checked={!!form.compatibilidadUniversal}
                  onChange={(e) => actualizar({ compatibilidadUniversal: e.target.checked })} />
                <span>
                  <strong>Compatibilidad universal</strong>
                  <span>Aceites, líquidos e insumos que sirven a cualquier vehículo. Los talleres lo verán como compatible.</span>
                </span>
              </label>

              {!form.compatibilidadUniversal && (
                <>
                  {form.modelosCompatibles.map((comp, index) => {
                    const modelos = comp.marca ? (MARCAS_VEHICULO[comp.marca] || []) : [];
                    const ce = compatErrores[index] || {};
                    const n = index + 1;
                    return (
                      <fieldset key={index} className="pfm-vehiculo">
                        <legend className="pfm-vehiculo__titulo">Vehículo {n}</legend>
                        {form.modelosCompatibles.length > 1 && (
                          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico pfm-vehiculo__quitar"
                            onClick={() => eliminarCompatibilidad(index)} aria-label={`Quitar el vehículo ${n}`}>
                            <Icon name="trash" size={14} /> Quitar
                          </button>
                        )}
                        <div className="ui-form__campos ui-form__campos--2">
                          <Campo id={`pfm-v${n}-marca`} label="Marca" error={ce.marca}>
                            <select className="ui-field__input" name="marca" value={comp.marca} onChange={(e) => handleCompatibilidadChange(index, e)}>
                              <option value="">Elegir marca</option>
                              {MARCAS_VEHICULO_LISTA.map((m) => <option key={m} value={m}>{m}</option>)}
                            </select>
                          </Campo>
                          <Campo id={`pfm-v${n}-modelo`} label="Modelo" error={ce.modelo}>
                            <select className="ui-field__input" name="modelo" value={comp.modelo} disabled={!comp.marca}
                              onChange={(e) => handleCompatibilidadChange(index, e)}>
                              <option value="">{comp.marca ? "Elegir modelo" : "Primero elige la marca"}</option>
                              {modelos.map((mo) => <option key={mo} value={mo}>{mo}</option>)}
                            </select>
                          </Campo>
                          <Campo id={`pfm-v${n}-desde`} label="Año desde" error={ce.anoDesde}>
                            <input className="ui-field__input" type="number" name="anoDesde" value={comp.anoDesde} min="1900" max={ANO_ACTUAL + 1}
                              onChange={(e) => handleCompatibilidadChange(index, e)} placeholder="Ej. 2015" />
                          </Campo>
                          <Campo id={`pfm-v${n}-hasta`} label="Año hasta" error={ce.anoHasta}>
                            <input className="ui-field__input" type="number" name="anoHasta" value={comp.anoHasta} min="1900" max={ANO_ACTUAL + 2}
                              onChange={(e) => handleCompatibilidadChange(index, e)} placeholder="Ej. 2023" />
                          </Campo>
                        </div>
                        <Campo id={`pfm-v${n}-motor`} label="Motor" opcional ayuda="Vacío: sirve para cualquier motor.">
                          <input className="ui-field__input ui-form__corto" name="motor" value={comp.motor || ""}
                            onChange={(e) => handleCompatibilidadChange(index, e)} placeholder="Ej. 1.6" />
                        </Campo>
                      </fieldset>
                    );
                  })}
                  <button type="button" className="ui-btn ui-btn--secundario pfm-agregar" onClick={agregarCompatibilidad}>
                    <Icon name="plus" size={16} /> Agregar otro vehículo
                  </button>
                </>
              )}
            </div>
          </SeccionEditable>
        </div>

        <div className="pfm-columna pfm-columna--lateral">
          <SeccionEditable id="pfm-precio" icono="tool" titulo="Precio e inventario">
            <div className="ui-form">
              <div className="ui-form__campos ui-form__campos--2">
                <Campo id="pfm-compra" label="Precio de compra" error={errores.precioCompra}>
                  <Dinero name="precioCompra" value={form.precioCompra} onChange={handleChange} />
                </Campo>
                <Campo id="pfm-venta" label="Precio de venta (sin IVA)" error={errores.precioVenta}
                  ayuda={paraCliente != null
                    ? `El cliente verá: ${formatoCop(paraCliente)} con IVA (${Math.round(ivaRate * 100)} %)`
                    : "DriveMaster le suma el IVA al cobrar."}>
                  <Dinero name="precioVenta" value={form.precioVenta} onChange={handleChange} />
                </Campo>
              </div>
              {margen !== null && (
                <p className={`pfm-margen${margen < 0 ? " pfm-margen--negativo" : ""}`} role="status">
                  Margen de ganancia <strong>{margen.toLocaleString("es-CO")} %</strong>
                </p>
              )}
              <div className="ui-form__campos ui-form__campos--2">
                <Campo id="pfm-stock" label="Stock actual" error={errores.stockActual} ayuda="Unidades en bodega.">
                  <input className="ui-field__input" name="stockActual" type="text" inputMode="numeric" value={form.stockActual} onChange={handleChange} placeholder="0" />
                </Campo>
                <Campo id="pfm-minimo" label="Stock mínimo" error={errores.stockMinimo} ayuda="Te avisamos al llegar aquí.">
                  <input className="ui-field__input" name="stockMinimo" type="text" inputMode="numeric" value={form.stockMinimo} onChange={handleChange} placeholder="0" />
                </Campo>
              </div>
            </div>
          </SeccionEditable>

          <SeccionEditable id="pfm-foto" icono="camera" titulo="Foto del producto"
            descripcion="Una foto clara sobre fondo liso ayuda a vender. JPG, PNG, WEBP o GIF, hasta 5 MB.">
            <div className="pfm-foto">
              <ImagenProducto src={imagenPreview} alt={imagenPreview ? "Foto del producto" : ""} className="pfm-foto__imagen" tamanioIcono={36} />
              <div className="pfm-foto__acciones">
                <button type="button" className="ui-btn ui-btn--secundario" onClick={() => fileInputRef.current?.click()}
                  aria-describedby={errorFoto ? "pfm-foto-error" : undefined}>
                  <Icon name="camera" size={16} /> {imagenPreview ? "Cambiar foto" : "Elegir foto"}
                </button>
                {imagenPreview && (
                  <button type="button" className="ui-btn ui-btn--texto" onClick={eliminarImagenSeleccionada}>Quitar foto</button>
                )}
              </div>
              {errorFoto && <p id="pfm-foto-error" className="ui-field__error" role="alert"><Icon name="alert" size={14} />{errorFoto}</p>}
              <input ref={fileInputRef} type="file" accept={TIPOS_IMAGEN.join(",")} onChange={handleImagenChange} hidden />
            </div>
          </SeccionEditable>

          <div className="pfm-guardar">
            <button type="submit" className="ui-btn ui-btn--primario ui-btn--grande ui-btn--bloque" disabled={guardando} aria-busy={guardando}>
              {textoGuardar}
            </button>
            <button type="button" className="ui-btn ui-btn--secundario ui-btn--bloque" onClick={onVolver} disabled={guardando}>
              Descartar cambios
            </button>
          </div>
        </div>
      </form>

      <BarraAccionMovil etiqueta={esEdicion ? "Editando" : "Producto nuevo"} nota={form.nombre || undefined}>
        <button type="submit" form="pfm-form" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
          {guardando ? "Guardando…" : "Guardar"}
        </button>
      </BarraAccionMovil>
    </div>
  );
}

export default ProductoForm;
