import { useEffect, useState } from "react";
import { crearProducto, actualizarProducto, obtenerProducto } from "../services/productoService";
import "../css/productos.css";
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
  const [error, setError] = useState(null);
  const [step, setStep] = useState(1);

  useEffect(() => {
    if (esEdicion) obtenerProducto(id).then(setForm).catch(err => setError(err.message));
  }, [id]);

  const handleChange = e => setForm({ ...form, [e.target.name]: e.target.value });

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
      esEdicion ? await actualizarProducto(id, form) : await crearProducto(form);
      onVolver();
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  };

  const margen = form.precioVenta && form.precioCompra
    ? (((form.precioVenta - form.precioCompra) / form.precioCompra) * 100).toFixed(1)
    : null;

  return (
    <div className="form-container">
      <div className="form-page-header">
        <button onClick={onVolver} className="btn-back">← Volver</button>
        <h1>{esEdicion ? "✏️ Editar Producto" : "➕ Nuevo Producto"}</h1>
      </div>

      {error && <p className="estado error" style={{marginBottom:"1rem"}}>{error}</p>}

      {/* Step tabs */}
      <div className="step-tabs">
        <button className={`step-tab ${step === 1 ? "activo" : ""}`} onClick={() => setStep(1)}>
          <span className="step-num">01</span> Información Básica
        </button>
        <button className={`step-tab ${step === 2 ? "activo" : ""}`} onClick={() => setStep(2)}>
          <span className="step-num">02</span> Inventario y Precio
        </button>
        <button className={`step-tab ${step === 3 ? "activo" : ""}`} onClick={() => setStep(3)}>
          <span className="step-num">03</span> Compatibilidad
        </button>
      </div>

      <form onSubmit={handleSubmit}>
        {/* STEP 1 */}
        {step === 1 && (
          <div className="producto-form">
            <div className="step-section-title">
              <span className="step-badge">01</span>
              <div>
                <h3>Información Básica</h3>
                <p>Datos de identificación del producto</p>
              </div>
            </div>
            <div className="form-grid">
              <div className="form-group">
                <label>Código *</label>
                <input name="codigo" value={form.codigo} onChange={handleChange} placeholder="Ej: FIL-001" required />
              </div>
              <div className="form-group">
                <label>Nombre del Producto *</label>
                <input name="nombre" value={form.nombre} onChange={handleChange} placeholder="Ej: Filtro de aceite premium" required />
              </div>
              <div className="form-group">
                <label>Marca *</label>
                <select name="marca" value={form.marca} onChange={handleChange} required className="select-form">
                  <option value="">Seleccionar marca...</option>
                  {MARCAS.map(m => <option key={m}>{m}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label>Categoría del Producto *</label>
                <select name="categoria" value={form.categoria} onChange={handleChange} required className="select-form">
                  <option value="">Seleccionar categoría...</option>
                  {CATEGORIAS.map(c => <option key={c}>{c}</option>)}
                </select>
              </div>
            </div>
            <div className="form-actions">
              <button type="button" onClick={onVolver} className="btn-cancelar">Cancelar</button>
              <button type="button" onClick={() => setStep(2)} className="btn-guardar">Siguiente →</button>
            </div>
          </div>
        )}

        {/* STEP 2 */}
        {step === 2 && (
          <div className="producto-form">
            <div className="step-section-title">
              <span className="step-badge">02</span>
              <div>
                <h3>Inventario y Precio</h3>
                <p>Control de stock y precio de venta</p>
              </div>
            </div>
            <div className="form-grid">
              <div className="form-group">
                <label>Precio Compra *</label>
                <input name="precioCompra" type="number" value={form.precioCompra} onChange={handleChange} placeholder="0" required />
              </div>
              <div className="form-group">
                <label>Precio Venta *</label>
                <input name="precioVenta" type="number" value={form.precioVenta} onChange={handleChange} placeholder="0" required />
              </div>
              <div className="form-group">
                <label>Stock Actual *</label>
                <input name="stockActual" type="number" value={form.stockActual} onChange={handleChange} placeholder="0" required />
              </div>
              <div className="form-group">
                <label>Stock Mínimo *</label>
                <input name="stockMinimo" type="number" value={form.stockMinimo} onChange={handleChange} placeholder="0" required />
              </div>
            </div>

            {margen && (
              <div className="margen-box">
                <span>Margen de ganancia</span>
                <strong style={{ color: margen > 0 ? "var(--success)" : "var(--error)" }}>{margen}%</strong>
              </div>
            )}

            <div className="form-actions">
              <button type="button" onClick={() => setStep(1)} className="btn-cancelar">← Anterior</button>
              <button type="button" onClick={() => setStep(3)} className="btn-guardar">Siguiente →</button>
            </div>
          </div>
        )}

        {/* STEP 3 */}
        {step === 3 && (
          <div className="producto-form">
            <div className="step-section-title">
              <span className="step-badge">03</span>
              <div>
                <h3>Modelos Compatibles</h3>
                <p>Vehículos con los que es compatible este producto</p>
              </div>
            </div>

            {form.modelosCompatibles.map((comp, index) => (
              <div key={index} className="compatibilidad-row">
                <input placeholder="Marca vehículo" name="marca" value={comp.marca} onChange={e => handleCompatibilidadChange(index, e)} />
                <input placeholder="Modelo" name="modelo" value={comp.modelo} onChange={e => handleCompatibilidadChange(index, e)} />
                <input placeholder="Año desde" name="anoDesde" type="number" value={comp.anoDesde} onChange={e => handleCompatibilidadChange(index, e)} />
                <input placeholder="Año hasta" name="anoHasta" type="number" value={comp.anoHasta} onChange={e => handleCompatibilidadChange(index, e)} />
                <button type="button" onClick={() => eliminarCompatibilidad(index)} className="btn-eliminar">✕</button>
              </div>
            ))}
            <button type="button" onClick={agregarCompatibilidad} className="btn-agregar">+ Agregar modelo</button>

            <div className="form-actions">
              <button type="button" onClick={() => setStep(2)} className="btn-cancelar">← Anterior</button>
              <button type="submit" className="btn-guardar" disabled={loading}>
                {loading ? "Guardando..." : esEdicion ? "💾 Actualizar" : "✅ Crear Producto"}
              </button>
            </div>
          </div>
        )}
      </form>
    </div>
  );
}

export default ProductoForm;