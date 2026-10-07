import Icon from "./Icon";
import "../../css/landing.css";

/* ── Tira de marcas ────────────────────────────────────────────────────────
   El mockup mostraba logos fijos (Bosch, Mobil, NGK…). Aquí se listan las
   marcas realmente presentes en tu catálogo, con su conteo. */
export function MarcasStrip({ productos = [] }) {
  const marcas = Object.entries(
    productos.reduce((acc, p) => {
      if (!p.marca) return acc;
      acc[p.marca] = (acc[p.marca] || 0) + 1;
      return acc;
    }, {})
  )
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10);

  if (!marcas.length) return null;

  return (
    <div className="dm-container dm-marcas">
      <span className="dm-marcas__label">
        INGENIERÍA DE MARCAS
        <br />
        <strong>QUE CONOCES Y CONFÍAS</strong>
      </span>
      <div className="dm-marcas__list">
        {marcas.map(([nombre, count]) => (
          <div className="dm-marcas__logo" key={nombre} title={`${count} producto(s)`}>
            {nombre}
            <small>{count}</small>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ── Cómo funciona ────────────────────────────────────────────────────────
   El flujo del marketplace empieza en el taller: el diagnóstico lo hace un
   profesional, no el cliente eligiendo una pieza. */
export function Pasos({ onSolicitarDiagnostico, onIrServicios }) {
  const pasos = [
    {
      icon: "message",
      title: "Pide tu diagnóstico",
      text: "Cuéntanos qué le pasa a tu vehículo, con fotos si quieres. Tarifa fija, descontable si aceptas la reparación.",
      action: "SOLICITAR",
      onClick: onSolicitarDiagnostico,
    },
    {
      icon: "target",
      title: "Taller más cercano",
      text: "Asignamos tu solicitud al taller aprobado más cercano. Te propone una cita y un mecánico revisa tu vehículo.",
      action: "CERCA DE TI",
      onClick: onSolicitarDiagnostico,
    },
    {
      icon: "layers",
      title: "Cotización clara",
      text: "Recibes por correo el detalle: repuestos, mano de obra, impuestos y tiempo estimado. Tú decides.",
      action: "SIN SORPRESAS",
      onClick: onIrServicios,
    },
    {
      icon: "box",
      title: "Repuestos de tiendas",
      text: "El taller compara el inventario de todas las tiendas registradas y elige por disponibilidad, cercanía y precio.",
      action: "MEJOR OPCIÓN",
      onClick: onIrServicios,
    },
    {
      icon: "tool",
      title: "Instalación y seguimiento",
      text: "Agendas la instalación y ves el avance del trabajo hasta la entrega. Todo queda en el historial de tu vehículo.",
      action: "DE PRINCIPIO A FIN",
      onClick: onIrServicios,
    },
  ];

  return (
    <section className="dm-section dm-container" id="dm-pasos">
      <div className="dm-section-heading">
        <div>
          <div className="dm-eyebrow">
            <span className="dm-line" /> ACOMPAÑAMIENTO TOTAL
          </div>
          <h2>
            Así funciona <span>DriveMaster.</span>
          </h2>
        </div>
        <p>
          Del primer síntoma a la entrega del vehículo.
          <br />
          Un taller, las tiendas y tú, conectados.
        </p>
      </div>

      <div className="dm-steps dm-steps--flujo">
        {pasos.map((p, i) => (
          <button className="dm-step" key={p.title} onClick={p.onClick}>
            <div className="dm-step__top">
              <span className="dm-step__number">
                0{i + 1}
                <span>/</span>
              </span>
              <Icon name={p.icon} size={30} />
            </div>
            <h3>{p.title}</h3>
            <p>{p.text}</p>
            <span className="dm-step__bottom">
              <span>{p.action}</span>
              <Icon name="arrow" size={17} />
            </span>
          </button>
        ))}
      </div>
    </section>
  );
}

/* ── Ecosistema: cliente, taller y tiendas ─────────────────────────────── */
export function Ecosistema({ onSolicitarDiagnostico }) {
  return (
    <section className="dm-container dm-eco">
      <div className="dm-eco__art">
        <div className="dm-eco__orbit dm-eco__orbit--1" />
        <div className="dm-eco__orbit dm-eco__orbit--2" />
        <div className="dm-eco__orbit dm-eco__orbit--3" />
        <div className="dm-eco__center">
          <Icon name="tool" size={52} />
        </div>
        <span className="dm-eco__node dm-eco__node--1" title="Cliente"><Icon name="user" size={21} /></span>
        <span className="dm-eco__node dm-eco__node--2" title="Tiendas"><Icon name="box" size={21} /></span>
        <span className="dm-eco__node dm-eco__node--3" title="Tu vehículo"><Icon name="car" size={21} /></span>
        <div className="dm-eco__label">
          UN ECOSISTEMA. TODO CONECTADO.
          <span>CLIENTE → TALLER → TIENDA</span>
        </div>
      </div>

      <div className="dm-eco__copy">
        <div className="dm-eyebrow">
          <span className="dm-line" /> MARKETPLACE DE TALLERES Y AUTOPARTES
        </div>
        <h2>
          Un taller, varias tiendas,
          <br />
          un solo pago.
        </h2>
        <p>
          Tu taller diagnostica y cotiza con el inventario de todas las tiendas
          registradas. Apruebas la cotización, pagas una sola vez y nosotros
          coordinamos el pedido de los repuestos hasta el taller.
        </p>
        <button className="dm-btn dm-btn--dark" onClick={onSolicitarDiagnostico}>
          Solicitar diagnóstico <Icon name="arrow" size={18} />
        </button>
      </div>
    </section>
  );
}

/* ── Para talleres y tiendas ───────────────────────────────────────────── */
export function ParaNegocios({ onRegistroNegocio }) {
  const negocios = [
    {
      icon: "tool",
      tipo: "Talleres",
      title: "Recibe clientes de tu zona.",
      puntos: [
        "Solicitudes de diagnóstico asignadas por cercanía",
        "Inventario de todas las tiendas para cotizar",
        "Citas, mecánicos y avance de cada trabajo en un panel",
      ],
    },
    {
      icon: "box",
      tipo: "Tiendas de autopartes",
      title: "Vende a los talleres.",
      puntos: [
        "Publica tu catálogo con precio, stock y ubicación",
        "Recibe pedidos de talleres con el pago asegurado",
        "Despacha al taller; tú controlas tu inventario",
      ],
    },
  ];

  return (
    <section className="dm-section dm-container dm-negocios" id="dm-negocios">
      <div className="dm-section-heading">
        <div>
          <div className="dm-eyebrow">
            <span className="dm-line" /> PARA NEGOCIOS
          </div>
          <h2>
            ¿Tienes un taller <span>o una tienda?</span>
          </h2>
        </div>
        <p>
          Únete al marketplace. Revisamos cada registro
          <br />
          antes de activarlo.
        </p>
      </div>

      <div className="dm-negocios__grid">
        {negocios.map((n) => (
          <article className="dm-negocios__card" key={n.tipo}>
            <div className="dm-negocios__head">
              <Icon name={n.icon} size={26} />
              <span>{n.tipo}</span>
            </div>
            <h3>{n.title}</h3>
            <ul>
              {n.puntos.map((p) => (
                <li key={p}>
                  <Icon name="check" size={15} />
                  {p}
                </li>
              ))}
            </ul>
          </article>
        ))}
      </div>

      <div className="dm-negocios__cta">
        <button className="dm-btn dm-btn--primary" onClick={onRegistroNegocio}>
          Registra tu negocio <Icon name="arrow" size={18} />
        </button>
      </div>
    </section>
  );
}

/* ── Beneficios + métricas ────────────────────────────────────────────────
   Las métricas del mockup eran ilustrativas (+500 repuestos, +50 marcas).
   Aquí se calculan con los datos reales del catálogo. */
export function Beneficios({ productos = [], onIrVehiculo }) {
  const marcas = new Set(productos.map((p) => p.marca).filter(Boolean));
  const categorias = new Set(productos.map((p) => p.categoria).filter(Boolean));

  const beneficios = [
    {
      icon: "search",
      title: "Diagnóstico profesional",
      text: "Un mecánico revisa tu vehículo antes de cambiar cualquier pieza. Nada de adivinar.",
    },
    {
      icon: "shield",
      title: "Cotización transparente",
      text: "Repuestos, mano de obra e impuestos detallados. Apruebas antes de que se haga el trabajo.",
    },
    {
      icon: "layers",
      title: "Repuestos comparados",
      text: "El taller compara disponibilidad, cercanía y precio entre todas las tiendas registradas.",
    },
    {
      icon: "sliders",
      title: "Seguimiento",
      text: "Ves el avance de tu servicio y guardas el historial completo de tu vehículo.",
    },
  ];

  const metricas = [
    { valor: productos.length, sufijo: "+", label: "Repuestos catalogados" },
    { valor: categorias.size, sufijo: "", label: "Categorías disponibles" },
    { valor: marcas.size, sufijo: "", label: "Marcas en catálogo" },
  ];

  return (
    <section className="dm-section dm-container" id="dm-nosotros">
      <div className="dm-section-heading">
        <div>
          <div className="dm-eyebrow">
            <span className="dm-line" /> HECHO PARA EL MUNDO AUTOMOTRIZ
          </div>
          <h2>
            No solo vendemos piezas.
            <br />
            <span>Te acompañamos.</span>
          </h2>
        </div>
        <p>
          El diagnóstico correcto. La cotización clara.
          <br />
          La confianza de saber qué le hacen a tu vehículo.
        </p>
      </div>

      <div className="dm-benefits">
        {beneficios.map((b) => (
          <div className="dm-benefit" key={b.title}>
            <Icon name={b.icon} size={25} />
            <h3>
              {b.title}
              <span>.</span>
            </h3>
            <p>{b.text}</p>
          </div>
        ))}
      </div>

      <div className="dm-metrics">
        <div className="dm-metrics__intro">
          <span className="dm-eyebrow">CATÁLOGO DRIVEMASTER</span>
          <p>Datos reales del inventario para compra directa.</p>
        </div>
        {metricas.map((m) => (
          <div key={m.label}>
            <strong>
              {m.sufijo && m.sufijo === "+" ? "+" : ""}
              {m.valor}
              <span>↗</span>
            </strong>
            <p>{m.label}</p>
          </div>
        ))}
      </div>

      <div className="dm-catalog-note">
        <Icon name="car" size={18} />
        <span>Tu vehículo es único. Sus repuestos también deberían serlo.</span>
        <button onClick={onIrVehiculo}>
          Identificar mi vehículo <Icon name="arrow" size={15} />
        </button>
      </div>
    </section>
  );
}

/* ── Banner de cierre ───────────────────────────────────────────────────── */
export function CierreBanner({ onSolicitarDiagnostico }) {
  return (
    <section className="dm-cierre">
      <div className="dm-container">
        <div>
          <span className="dm-eyebrow">UN TALLER CERCA DE TI LO REVISA.</span>
          <h2>¿Algo suena raro en tu vehículo?</h2>
        </div>
        <button className="dm-btn dm-btn--primary" onClick={onSolicitarDiagnostico}>
          Solicitar diagnóstico <Icon name="arrow" size={19} />
        </button>
      </div>
    </section>
  );
}