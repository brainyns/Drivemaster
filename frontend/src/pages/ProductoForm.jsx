import { useEffect, useState } from "react";
import { crearProducto, actualizarProducto, obtenerProducto } from "../services/productoService";
import "../css/producto-form.css";

const CATEGORIAS = [
  "Motor (pistones, válvulas, filtros, bombas)",
  "Transmisión y Caja",
  "Frenos y Suspensión",
  "Eléctrico y Baterías",
  "Carrocería y Accesorios",
  "Lubricantes y Fluidos",
  "Neumáticos y Llantas",
  "Enfriamiento y Radiador",
  "Audio y Multimedia",
  "Otro",
];

const MARCAS = ["Toyota","Chevrolet","Ford","Renault","Kia","Hyundai","Nissan","AC Delco","Bosch","Gates","Otro"];

function ProductoForm({ id, onVolver }) {
  const esEdicion = Boolean(id);

  const [form, setForm] = useState({
    codigo: "", nombre: "", categoria: "", marca: "",
    precioCompra: "", precioVenta: "",
    stockActual: "", stockMinimo: "",
    modelosCompatibles: [{ marca: "", modelo: "", anoDesde: "", anoHasta: "" }],
  });

  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState(null);

  useEffect(() => {
    if (esEdicion) obtenerProducto(id).then(setForm).catch(e => setError(e.message));
  }, [id]);

  const handleChange = e => {
    const { name, value } = e.target;
    setForm({ ...form, [name]: value });
  };

  const handleCompatibilidadChange = (index, e) => {
    const nuevos = [...form.modelosCompatibles];
    nuevos[index][e.target.name] = e.target.value;
    setForm({ ...form, modelosCompatibles: nuevos });
  };

  const agregarCompatibilidad = () => setForm({
    ...form,
    modelosCompatibles: [...form.modelosCompatibles, { marca: "", modelo: "", anoDesde: "", anoHasta: "" }]
  });

  const eliminarCompatibilidad = index => setForm({
    ...form,
    modelosCompatibles: form.modelosCompatibles.filter((_, i) => i !== index)
  });

  const handleSubmit = async e => {
    e.preventDefault();
    setLoading(true); setError(null);
    try {
      const payload = {
        ...form,
        precioCompra: Number(form.precioCompra),
        precioVenta:  Number(form.precioVenta),
        stockActual:  Number(form.stockActual),
        stockMinimo:  Number(form.stockMinimo),
      };
      esEdicion ? await actualizarProducto(id, payload) : await crearProducto(payload);
      onVolver();
    } catch(e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const compra = Number(form.precioCompra);
  const venta  = Number(form.precioVenta);
  const margen = compra > 0 && venta > 0
    ? (((venta - compra) / compra) * 100).toFixed(1)
    : null;

  return (
    <div className="pf-page">

      <header className="pf-topbar">
        <div className="pf-topbar-left">
          <span className="pf-topbar-title">{esEdicion ? "Editar Producto" : "Agregar Producto"}</span>
        </div>
        <div className="pf-topbar-right">
          <div className="pf-search-wrap">
            <span className="pf-search-icon">🔍</span>
            <input className="pf-topbar-search" placeholder="Buscar en catálogo..." />
          </div>
          <button type="button" className="pf-icon-btn">🔔</button>
          <button type="button" className="pf-icon-btn">⚙</button>
          <div className="pf-avatar-sm">A</div>
        </div>
      </header>

      <div className="pf-tabs">
        <button type="button" className="pf-tab pf-tab--active">
          + {esEdicion ? "Editar Producto" : "Agregar Producto"}
        </button>
        <button type="button" className="pf-tab" onClick={onVolver}>
          Paquete Ver Productos
        </button>
      </div>

      {error && <p className="pf-error">{error}</p>}

      <form onSubmit={handleSubmit} className="pf-layout">

        <div className="pf-left">

          <div className="pf-section">
            <div className="pf-section-header">
              <h3>Información General</h3>
            </div>

            <div className="pf-field pf-field--full">
              <label>Nombre del Producto</label>
              <input
                name="nombre" value={form.nombre} onChange={handleChange}
                placeholder="Ej. Pastillas de Freno Cerámicas Brembo" required
              />
            </div>

            <div className="pf-row">
              <div className="pf-field">
                <label>Categoría</label>
                <select name="categoria" value={form.categoria} onChange={handleChange} required>
                  <option value="">Seleccionar categoría</option>
                  {CATEGORIAS.map(c => <option key={c}>{c}</option>)}
                </select>
              </div>
              <div className="pf-field">
                <label>SKU / Código</label>
                <input
                  name="codigo" value={form.codigo} onChange={handleChange}
                  placeholder="Ej. KNT-BRK-001" required
                />
              </div>
            </div>

            <div className="pf-field pf-field--full">
              <label>Marca</label>
              <select name="marca" value={form.marca} onChange={handleChange} required>
                <option value="">Seleccionar marca</option>
                {MARCAS.map(m => <option key={m}>{m}</option>)}
              </select>
            </div>
          </div>

          <div className="pf-section">
            <div className="pf-section-header">
              <h3>Modelos Compatibles</h3>
            </div>

            {form.modelosCompatibles.map((comp, index) => (
              <div key={index} className="pf-compat-row">
                <input placeholder="Marca vehículo" name="marca" value={comp.marca}
                  onChange={e => handleCompatibilidadChange(index, e)} />
                <input placeholder="Modelo" name="modelo" value={comp.modelo}
                  onChange={e => handleCompatibilidadChange(index, e)} />
                <input placeholder="Año desde" name="anoDesde" type="number" value={comp.anoDesde}
                  onChange={e => handleCompatibilidadChange(index, e)} />
                <input placeholder="Año hasta" name="anoHasta" type="number" value={comp.anoHasta}
                  onChange={e => handleCompatibilidadChange(index, e)} />
                <button type="button" onClick={() => eliminarCompatibilidad(index)} className="pf-compat-del">X</button>
              </div>
            ))}
            <button type="button" onClick={agregarCompatibilidad} className="pf-add-compat">
              + Agregar modelo compatible
            </button>
          </div>
        </div>

        <div className="pf-right">
          <div className="pf-section">
            <div className="pf-section-header">
              <h3>Precio e Inventario</h3>
            </div>

            <div className="pf-field pf-field--full">
              <label>Precio de Compra</label>
              <div className="pf-money-input">
                <span>$</span>
                <input
                  name="precioCompra"
                  type="text"
                  inputMode="decimal"
                  value={form.precioCompra}
                  onChange={handleChange}
                  placeholder="0"
                />
              </div>
            </div>

            <div className="pf-field pf-field--full">
              <label>Precio de Venta</label>
              <div className="pf-money-input">
                <span>$</span>
                <input
                  name="precioVenta"
                  type="text"
                  inputMode="decimal"
                  value={form.precioVenta}
                  onChange={handleChange}
                  placeholder="0"
                />
              </div>
            </div>

            {margen !== null && (
              <div className={`pf-margen ${Number(margen) >= 0 ? "pf-margen--pos" : "pf-margen--neg"}`}>
                <span>Margen de ganancia</span>
                <strong>{margen}%</strong>
              </div>
            )}

            <div className="pf-row">
              <div className="pf-field">
                <label>Stock Real</label>
                <div className="pf-stock-input">
                  <input
                    name="stockActual"
                    type="text"
                    inputMode="numeric"
                    value={form.stockActual}
                    onChange={handleChange}
                    placeholder="0"
                  />
                  <span>UNIDADES</span>
                </div>
              </div>
              <div className="pf-field">
                <label>Stock Mínimo</label>
                <div className="pf-stock-input">
                  <input
                    name="stockMinimo"
                    type="text"
                    inputMode="numeric"
                    value={form.stockMinimo}
                    onChange={handleChange}
                    placeholder="0"
                  />
                  <span>UNIDADES</span>
                </div>
              </div>
            </div>
          </div>

          <button type="submit" className="pf-btn-save" disabled={loading}>
            {loading ? "Guardando..." : esEdicion ? "Actualizar Producto" : "Guardar Producto"}
          </button>
          <button type="button" onClick={onVolver} className="pf-btn-cancel">
            Descartar Cambios
          </button>

          <div className="pf-info-card">
            <p className="pf-info-title">Sincronización Automática</p>
            <p className="pf-info-text">
              Los productos guardados se sincronizarán automáticamente con el inventario
              y estarán disponibles para facturación inmediata.
            </p>
          </div>
        </div>
      </form>
    </div>
  );
}

export default ProductoForm;