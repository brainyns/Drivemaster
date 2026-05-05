import { useEffect, useState } from "react";
import { crearProducto, actualizarProducto, obtenerProducto, listarProductos } from "../services/productoService";
import "../css/producto-form.css";

const CATEGORIAS = [
  "Motor",
  "Transmision y Embrague",
  "Frenos",
  "Suspension y Direccion",
  "Sistema Electrico",
  "Baterias y Carga",
  "Enfriamiento y Radiadores",
  "Lubricantes y Fluidos",
  "Filtros",
  "Escape y Emisiones",
  "Combustible e Inyeccion",
  "Aire Acondicionado y Climatizacion",
  "Carroceria Exterior",
  "Interior y Accesorios",
  "Iluminacion",
  "Neumaticos y Llantas",
  "Audio, Multimedia y Electronica",
  "Herramientas y Equipos",
  "Consumibles y Taller",
  "Otro",
];

const MARCAS_VEHICULO = {
  Toyota:       ["Corolla","Camry","RAV4","Highlander","Prius","Land Cruiser","Yaris","Hilux","Fortuner","4Runner","Avalon","C-HR","Venza","Sequoia","Tacoma","Tundra","GR86","Supra","FJ Cruiser","Rush","Innova","Avanza","Wigo","Starlet","Etios"],
  Mazda:        ["Mazda2","Mazda3","Mazda6","CX-3","CX-30","CX-5","CX-50","CX-60","CX-90","MX-5 Miata","MX-30","BT-50","RX-7","RX-8","626","323"],
  Kia:          ["Picanto","Rio","Cerato","Forte","Stinger","Sportage","Sorento","Telluride","Soul","Seltos","Niro","EV6","EV9","Carnival","Stonic","XCeed","ProCeed","K5","K8","K900","Mohave"],
  Renault:      ["Logan","Sandero","Duster","Kwid","Captur","Koleos","Megane","Clio","Fluence","Symbol","Oroch","Alaskan","Arkana","Zoe","Triber","Kardian","Stepway"],
  Chevrolet:    ["Spark","Sail","Aveo","Cruze","Malibu","Camaro","Corvette","Equinox","Trax","Trailblazer","Traverse","Tahoe","Suburban","Silverado","Colorado","Captiva","Onix","Tracker","Montana","Blazer","Bolt EV"],
  Hyundai:      ["i10","i20","i30","Elantra","Sonata","Accent","Tucson","Santa Fe","Palisade","Venue","Creta","Kona","Ioniq","Ioniq 5","Ioniq 6","Starex","H1","Veloster","Genesis","Nexo"],
  Ford:         ["Fiesta","Focus","Mustang","Fusion","Taurus","EcoSport","Escape","Edge","Explorer","Expedition","Bronco","Bronco Sport","Maverick","Ranger","F-150","F-250","Transit","Puma","Kuga","Territory"],
  Volkswagen:   ["Polo","Golf","Jetta","Passat","Arteon","Tiguan","T-Cross","T-Roc","Touareg","Atlas","ID.4","ID.3","Amarok","Caddy","Taos","Virtus","Nivus","Saveiro"],
  Nissan:       ["Versa","Sentra","Altima","Maxima","Tiida","Kicks","Qashqai","X-Trail","Murano","Pathfinder","Armada","Frontier","Navara","GT-R","370Z","400Z","Leaf","Ariya","Terra","NP300"],
  Honda:        ["Fit","Jazz","City","Civic","Accord","HR-V","CR-V","Pilot","Passport","Ridgeline","Odyssey","Element","BR-V","WR-V","ZR-V","Insight"],
  Suzuki:       ["Alto","Celerio","Swift","Baleno","Ciaz","Vitara","S-Cross","Ignis","Jimny","Grand Vitara","Ertiga","XL7","Fronx"],
  BMW:          ["116i","118i","120i","M2","M3","M4","M5","318i","320i","325i","330i","418i","420i","430i","520i","528i","530i","540i","730i","740i","750i","X1","X2","X3","X4","X5","X6","X7","iX","i4","i7","Z4"],
  "Mercedes-Benz": ["A180","A200","A250","C180","C200","C220","C300","E200","E250","E300","E350","S350","S400","S500","GLA180","GLA200","GLC200","GLC300","GLE300","GLE400","GLS350","GLS450","CLA200","CLA250","EQA","EQB","EQC","EQS","AMG GT"],
  Audi:         ["A1","A3","A4","A5","A6","A7","A8","Q2","Q3","Q5","Q7","Q8","TT","R8","e-tron","e-tron GT","RS3","RS4","RS5","RS6","RS7","S3","S4","S5","S6","S7","S8","SQ5","SQ7","SQ8"],
  Jeep:         ["Renegade","Compass","Cherokee","Grand Cherokee","Wrangler","Gladiator","Commander","Avenger","Grand Wagoneer"],
  Mitsubishi:   ["Mirage","Attrage","Lancer","Galant","Eclipse Cross","ASX","Outlander","Outlander Sport","Pajero","Montero","L200","Triton","Xpander","Xforce"],
  Subaru:       ["Impreza","Legacy","Outback","Forester","XV","Crosstrek","Ascent","BRZ","WRX","WRX STI","Solterra"],
  Volvo:        ["S60","S90","V60","V90","XC40","XC60","XC90","C40","EX30","EX90"],
  Peugeot:      ["208","308","408","508","2008","3008","5008","Landtrek","Partner","Expert","Boxer"],
  Fiat:         ["500","Mobi","Argo","Cronos","Siena","Pulse","Fastback","Toro","Doblo","Ducato"],
  Dodge:        ["Neon","Charger","Challenger","Durango","Journey","RAM 1500","RAM 2500","Hornet","Viper"],
  "Land Rover": ["Defender","Discovery","Discovery Sport","Freelander","Range Rover","Range Rover Sport","Range Rover Evoque","Range Rover Velar"],
  Porsche:      ["911","718 Cayman","718 Boxster","Taycan","Panamera","Macan","Cayenne"],
  Lexus:        ["IS","ES","GS","LS","UX","NX","RX","GX","LX","RC","LC","RZ"],
  "Alfa Romeo": ["Giulia","Stelvio","Tonale","Giulietta","MiTo","4C","Spider"],
  Seat:         ["Ibiza","Leon","Arona","Ateca","Tarraco","Mii","Alhambra","Toledo"],
  Skoda:        ["Fabia","Octavia","Superb","Kamiq","Karoq","Kodiaq","Enyaq","Scala","Rapid"],
  MINI:         ["Cooper","Cooper S","Clubman","Countryman","Paceman","Convertible","JCW"],
  Tesla:        ["Model 3","Model Y","Model S","Model X","Cybertruck","Roadster"],
  BYD:          ["Atto 3","Han","Tang","Song Plus","Seal","Dolphin","Yuan Plus","Shark"],
  Chery:        ["QQ","Tiggo 2","Tiggo 4","Tiggo 7","Tiggo 8","Arrizo 5","Arrizo 6","Omoda 5","Omoda C9"],
  JAC:          ["J2","J3","J4","S2","S3","S4","S5","T6","T8","EV"],
  MG:           ["MG3","MG5","MG6","ZS","HS","RX5","RX8","One","Marvel R","EHS"],
  Geely:        ["Coolray","Okavango","Emgrand","Azkarra","BL","MK","EC7"],
  Ssangyong:    ["Tivoli","Korando","Rexton","Musso","Actyon","Rodius"],
  Isuzu:        ["D-Max","MU-X","Trooper","Crosswind","Sportivo"],
  RAM:          ["700","1000","1500","2500","3500","ProMaster"],
  GMC:          ["Sierra","Canyon","Terrain","Acadia","Yukon"],
  Cadillac:     ["CT4","CT5","XT4","XT5","XT6","Escalade","LYRIQ"],
  Ferrari:      ["Roma","Portofino","SF90","812","F8","296","GTC4Lusso","Purosangue"],
  Lamborghini:  ["Huracan","Aventador","Urus","Revuelto","Sterrato"],
  Maserati:     ["Ghibli","Quattroporte","Levante","Grecale","MC20","GranTurismo"],
};

const MARCAS_REPUESTO = [
  "AC Delco","Aisin","Akebono","Behr","Bilstein","Bosch","Brembo","Continental",
  "Dayco","Denso","Delphi","Exide","Fag","Ferodo","Fram","Gates","Hella","KYB",
  "LUK","Mahle","Mann Filter","Moog","Monroe","Motul","Moog","NGK","NTN",
  "Pentosin","Sachs","Schaeffler","Shell","SKF","Textar","TRW","Valeo","VDO",
  "Victor Reinz","ZF",
  "Toyota","Chevrolet","Ford","Renault","Kia","Hyundai","Nissan","Honda",
  "Suzuki","Mazda","Volkswagen","BMW","Mercedes-Benz","Audi","Mitsubishi",
  "Otro",
].sort((a, b) => a.localeCompare(b));

const MARCAS_VEHICULO_LISTA = Object.keys(MARCAS_VEHICULO).sort((a, b) => a.localeCompare(b));

const ANO_ACTUAL = new Date().getFullYear();

const campoVacio = v => v === null || v === undefined || String(v).trim() === "";

function validarFormulario(form, productosExistentes, esEdicion, id) {
  const errores = {};

  if (campoVacio(form.nombre)) errores.nombre = "El nombre es obligatorio.";
  else if (form.nombre.trim().length < 3) errores.nombre = "El nombre debe tener al menos 3 caracteres.";

  if (campoVacio(form.categoria)) errores.categoria = "Seleccione una categoria.";
  if (campoVacio(form.codigo)) errores.codigo = "El codigo SKU es obligatorio.";
  else {
    const duplicado = productosExistentes.find(
      p => p.codigo?.toLowerCase() === form.codigo.trim().toLowerCase() &&
           (!esEdicion || String(p.id) !== String(id))
    );
    if (duplicado) errores.codigo = "Ya existe un producto con este codigo SKU.";
  }

  if (campoVacio(form.marca)) errores.marca = "Seleccione una marca.";

  const compra = Number(form.precioCompra);
  const venta  = Number(form.precioVenta);
  if (campoVacio(form.precioCompra) || isNaN(compra) || compra < 0)
    errores.precioCompra = "Ingrese un precio de compra valido.";
  if (campoVacio(form.precioVenta) || isNaN(venta) || venta < 0)
    errores.precioVenta = "Ingrese un precio de venta valido.";
  else if (compra > 0 && venta > 0 && venta < compra)
    errores.precioVenta = "El precio de venta no puede ser menor al de compra.";

  const stockActual  = Number(form.stockActual);
  const stockMinimo  = Number(form.stockMinimo);
  if (campoVacio(form.stockActual) || isNaN(stockActual) || !Number.isInteger(stockActual) || stockActual < 0)
    errores.stockActual = "Ingrese un stock actual valido (numero entero mayor o igual a 0).";
  if (campoVacio(form.stockMinimo) || isNaN(stockMinimo) || !Number.isInteger(stockMinimo) || stockMinimo < 0)
    errores.stockMinimo = "Ingrese un stock minimo valido (numero entero mayor o igual a 0).";

  const erroresCompat = [];
  form.modelosCompatibles.forEach((comp, i) => {
    const e = {};
    if (campoVacio(comp.marca))   e.marca  = "Seleccione la marca del vehiculo.";
    if (campoVacio(comp.modelo))  e.modelo = "Seleccione el modelo.";
    const desde = Number(comp.anoDesde);
    const hasta = Number(comp.anoHasta);
    if (campoVacio(comp.anoDesde) || isNaN(desde) || desde < 1900 || desde > ANO_ACTUAL + 1)
      e.anoDesde = "Ano desde invalido.";
    if (campoVacio(comp.anoHasta) || isNaN(hasta) || hasta < 1900 || hasta > ANO_ACTUAL + 2)
      e.anoHasta = "Ano hasta invalido.";
    else if (!e.anoDesde && hasta < desde)
      e.anoHasta = "El ano hasta no puede ser anterior al ano desde.";
    if (Object.keys(e).length) erroresCompat[i] = e;
  });
  if (erroresCompat.some(Boolean)) errores.compatibilidad = erroresCompat;

  return errores;
}

function FieldError({ msg }) {
  if (!msg) return null;
  return <span className="pf-field-error">{msg}</span>;
}

function ProductoForm({ id, onVolver, token }) {
  const esEdicion = Boolean(id);

  const [form, setForm] = useState({
    codigo: "", nombre: "", categoria: "", marca: "",
    precioCompra: "", precioVenta: "",
    stockActual: "", stockMinimo: "",
    modelosCompatibles: [{ marca: "", modelo: "", anoDesde: "", anoHasta: "" }],
  });

  const [errores,            setErrores]            = useState({});
  const [productosExistentes, setProductosExistentes] = useState([]);
  const [loading,            setLoading]            = useState(false);
  const [serverError,        setServerError]        = useState(null);
  const [submitted,          setSubmitted]          = useState(false);

  useEffect(() => {
    listarProductos(token)
      .then(setProductosExistentes)
      .catch(() => {});
  }, [token]);

  useEffect(() => {
    if (esEdicion) {
      obtenerProducto(id, token)
        .then(data => setForm({
          ...data,
          modelosCompatibles: data.modelosCompatibles?.length
            ? data.modelosCompatibles
            : [{ marca: "", modelo: "", anoDesde: "", anoHasta: "" }],
        }))
        .catch(e => setServerError(e.message));
    }
  }, [id, token]);

  // Revalidar en tiempo real solo si ya se intentó enviar
  useEffect(() => {
    if (submitted) {
      const e = validarFormulario(form, productosExistentes, esEdicion, id);
      setErrores(e);
    }
  }, [form, submitted]);

  const handleChange = e => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
  };

  const handleCompatibilidadChange = (index, e) => {
    const { name, value } = e.target;
    setForm(prev => {
      const nuevos = prev.modelosCompatibles.map((c, i) =>
        i === index ? { ...c, [name]: value, ...(name === "marca" ? { modelo: "" } : {}) } : c
      );
      return { ...prev, modelosCompatibles: nuevos };
    });
  };

  const agregarCompatibilidad = () => {
    setForm(prev => ({
      ...prev,
      modelosCompatibles: [...prev.modelosCompatibles, { marca: "", modelo: "", anoDesde: "", anoHasta: "" }],
    }));
  };

  const eliminarCompatibilidad = index => {
    setForm(prev => ({
      ...prev,
      modelosCompatibles: prev.modelosCompatibles.filter((_, i) => i !== index),
    }));
  };

  const handleSubmit = async e => {
    e.preventDefault();
    setSubmitted(true);
    const e2 = validarFormulario(form, productosExistentes, esEdicion, id);
    setErrores(e2);
    if (Object.keys(e2).length > 0) return;

    setLoading(true);
    setServerError(null);
    try {
      const payload = {
        ...form,
        nombre:       form.nombre.trim(),
        codigo:       form.codigo.trim().toUpperCase(),
        precioCompra: Number(form.precioCompra),
        precioVenta:  Number(form.precioVenta),
        stockActual:  Number(form.stockActual),
        stockMinimo:  Number(form.stockMinimo),
        modelosCompatibles: form.modelosCompatibles.map(c => ({
          ...c,
          anoDesde: Number(c.anoDesde),
          anoHasta: Number(c.anoHasta),
        })),
      };
      esEdicion
        ? await actualizarProducto(id, payload, token)
        : await crearProducto(payload, token);
      onVolver();
    } catch (err) {
      setServerError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const compra = Number(form.precioCompra);
  const venta  = Number(form.precioVenta);
  const margen = compra > 0 && venta > 0
    ? (((venta - compra) / compra) * 100).toFixed(1)
    : null;

  const compatErrores = errores.compatibilidad || [];

  return (
    <div className="pf-page">

      <header className="pf-topbar">
        <div className="pf-topbar-left">
          <span className="pf-topbar-title">
            {esEdicion ? "Editar Producto" : "Agregar Producto"}
          </span>
        </div>
        <div className="pf-topbar-right">
          <div className="pf-search-wrap">
            <span className="pf-search-icon">&#128269;</span>
            <input className="pf-topbar-search" placeholder="Buscar en catalogo..." />
          </div>
          <button type="button" className="pf-icon-btn">&#128276;</button>
          <button type="button" className="pf-icon-btn">&#9881;</button>
          <div className="pf-avatar-sm">A</div>
        </div>
      </header>

      <div className="pf-tabs">
        <button type="button" className="pf-tab pf-tab--active">
          {esEdicion ? "Editar Producto" : "Nuevo Producto"}
        </button>
        <button type="button" className="pf-tab" onClick={onVolver}>
          Ver Productos
        </button>
      </div>

      {serverError && <p className="pf-error">{serverError}</p>}

      <form onSubmit={handleSubmit} noValidate className="pf-layout">

        {/* ─── COLUMNA IZQUIERDA ─── */}
        <div className="pf-left">

          {/* Informacion General */}
          <div className="pf-section">
            <div className="pf-section-header">
              <h3>Informacion General</h3>
            </div>

            <div className="pf-field pf-field--full">
              <label>Nombre del Producto</label>
              <input
                name="nombre"
                value={form.nombre}
                onChange={handleChange}
                placeholder="Ej. Pastillas de Freno Ceramicas Brembo"
                className={errores.nombre ? "pf-input--error" : ""}
              />
              <FieldError msg={errores.nombre} />
            </div>

            <div className="pf-row">
              <div className="pf-field">
                <label>Categoria</label>
                <select
                  name="categoria"
                  value={form.categoria}
                  onChange={handleChange}
                  className={errores.categoria ? "pf-input--error" : ""}
                >
                  <option value="">Seleccionar categoria</option>
                  {CATEGORIAS.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
                <FieldError msg={errores.categoria} />
              </div>

              <div className="pf-field">
                <label>SKU / Codigo</label>
                <input
                  name="codigo"
                  value={form.codigo}
                  onChange={handleChange}
                  placeholder="Ej. KNT-BRK-001"
                  className={errores.codigo ? "pf-input--error" : ""}
                />
                <FieldError msg={errores.codigo} />
              </div>
            </div>

            <div className="pf-field pf-field--full">
              <label>Marca del Repuesto</label>
              <select
                name="marca"
                value={form.marca}
                onChange={handleChange}
                className={errores.marca ? "pf-input--error" : ""}
              >
                <option value="">Seleccionar marca</option>
                {MARCAS_REPUESTO.map(m => <option key={m} value={m}>{m}</option>)}
              </select>
              <FieldError msg={errores.marca} />
            </div>
          </div>

          {/* Modelos Compatibles */}
          <div className="pf-section">
            <div className="pf-section-header">
              <h3>Modelos Compatibles</h3>
            </div>

            {form.modelosCompatibles.map((comp, index) => {
              const modelosDisponibles = comp.marca ? (MARCAS_VEHICULO[comp.marca] || []) : [];
              const ce = compatErrores[index] || {};
              return (
                <div key={index} className="pf-compat-block">
                  <div className="pf-compat-header">
                    <span className="pf-compat-num">Vehiculo {index + 1}</span>
                    {form.modelosCompatibles.length > 1 && (
                      <button
                        type="button"
                        onClick={() => eliminarCompatibilidad(index)}
                        className="pf-compat-del"
                      >
                        Eliminar
                      </button>
                    )}
                  </div>

                  <div className="pf-compat-grid">
                    <div className="pf-field">
                      <label>Marca del Vehiculo</label>
                      <select
                        name="marca"
                        value={comp.marca}
                        onChange={e => handleCompatibilidadChange(index, e)}
                        className={ce.marca ? "pf-input--error" : ""}
                      >
                        <option value="">Seleccionar marca</option>
                        {MARCAS_VEHICULO_LISTA.map(m => (
                          <option key={m} value={m}>{m}</option>
                        ))}
                      </select>
                      <FieldError msg={ce.marca} />
                    </div>

                    <div className="pf-field">
                      <label>Modelo</label>
                      <select
                        name="modelo"
                        value={comp.modelo}
                        onChange={e => handleCompatibilidadChange(index, e)}
                        disabled={!comp.marca}
                        className={ce.modelo ? "pf-input--error" : ""}
                      >
                        <option value="">
                          {comp.marca ? "Seleccionar modelo" : "Primero seleccione marca"}
                        </option>
                        {modelosDisponibles.map(mo => (
                          <option key={mo} value={mo}>{mo}</option>
                        ))}
                      </select>
                      <FieldError msg={ce.modelo} />
                    </div>

                    <div className="pf-field">
                      <label>Ano Desde</label>
                      <input
                        type="number"
                        name="anoDesde"
                        value={comp.anoDesde}
                        onChange={e => handleCompatibilidadChange(index, e)}
                        placeholder="Ej. 2015"
                        min="1900"
                        max={ANO_ACTUAL + 1}
                        className={ce.anoDesde ? "pf-input--error" : ""}
                      />
                      <FieldError msg={ce.anoDesde} />
                    </div>

                    <div className="pf-field">
                      <label>Ano Hasta</label>
                      <input
                        type="number"
                        name="anoHasta"
                        value={comp.anoHasta}
                        onChange={e => handleCompatibilidadChange(index, e)}
                        placeholder="Ej. 2023"
                        min="1900"
                        max={ANO_ACTUAL + 2}
                        className={ce.anoHasta ? "pf-input--error" : ""}
                      />
                      <FieldError msg={ce.anoHasta} />
                    </div>
                  </div>
                </div>
              );
            })}

            <button
              type="button"
              onClick={agregarCompatibilidad}
              className="pf-add-compat"
            >
              + Agregar vehiculo compatible
            </button>
          </div>
        </div>

        {/* ─── COLUMNA DERECHA ─── */}
        <div className="pf-right">
          <div className="pf-section">
            <div className="pf-section-header">
              <h3>Precio e Inventario</h3>
            </div>

            <div className="pf-field pf-field--full">
              <label>Precio de Compra</label>
              <div className={`pf-money-input${errores.precioCompra ? " pf-money-input--error" : ""}`}>
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
              <FieldError msg={errores.precioCompra} />
            </div>

            <div className="pf-field pf-field--full">
              <label>Precio de Venta</label>
              <div className={`pf-money-input${errores.precioVenta ? " pf-money-input--error" : ""}`}>
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
              <FieldError msg={errores.precioVenta} />
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
                <div className={`pf-stock-input${errores.stockActual ? " pf-stock-input--error" : ""}`}>
                  <input
                    name="stockActual"
                    type="text"
                    inputMode="numeric"
                    value={form.stockActual}
                    onChange={handleChange}
                    placeholder="0"
                  />
                  <span>UND</span>
                </div>
                <FieldError msg={errores.stockActual} />
              </div>

              <div className="pf-field">
                <label>Stock Minimo</label>
                <div className={`pf-stock-input${errores.stockMinimo ? " pf-stock-input--error" : ""}`}>
                  <input
                    name="stockMinimo"
                    type="text"
                    inputMode="numeric"
                    value={form.stockMinimo}
                    onChange={handleChange}
                    placeholder="0"
                  />
                  <span>UND</span>
                </div>
                <FieldError msg={errores.stockMinimo} />
              </div>
            </div>
          </div>

          {submitted && Object.keys(errores).length > 0 && (
            <div className="pf-validation-summary">
              Revise los campos marcados antes de continuar.
            </div>
          )}

          <button type="submit" className="pf-btn-save" disabled={loading}>
            {loading ? "Guardando..." : esEdicion ? "Actualizar Producto" : "Guardar Producto"}
          </button>
          <button type="button" onClick={onVolver} className="pf-btn-cancel">
            Descartar Cambios
          </button>

          <div className="pf-info-card">
            <p className="pf-info-title">Sincronizacion Automatica</p>
            <p className="pf-info-text">
              Los productos guardados se sincronizaran automaticamente con el inventario
              y estaran disponibles para facturacion inmediata.
            </p>
          </div>
        </div>
      </form>
    </div>
  );
}

export default ProductoForm;