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

/* ── Cómo funciona ─────────────────────────────────────────────────────── */
export function Pasos({ onIrVehiculo, onVerCatalogo, onIrServicios }) {
  const pasos = [
    {
      icon: "car",
      title: "Identifica tu vehículo",
      text: "Registra marca, modelo, año y motor. Entendemos tu vehículo antes de recomendarte una pieza.",
      action: "EMPIEZA CON TU VEHÍCULO",
      onClick: onIrVehiculo,
    },
    {
      icon: "target",
      title: "Encuentra repuestos compatibles",
      text: "Compara marcas, especificaciones y alternativas que realmente se ajustan a lo que necesitas.",
      action: "ELIGE CON CONFIANZA",
      onClick: onVerCatalogo,
    },
    {
      icon: "tool",
      title: "Compra o solicita el servicio",
      text: "Elige tu repuesto y da el siguiente paso. De la pieza correcta al mantenimiento completo.",
      action: "SIGUE AVANZANDO",
      onClick: onIrServicios,
    },
  ];

  return (
    <section className="dm-section dm-container" id="dm-pasos">
      <div className="dm-section-heading">
        <div>
          <div className="dm-eyebrow">
            <span className="dm-line" /> MENOS BÚSQUEDA. MÁS CERTEZA.
          </div>
          <h2>
            Tu próximo paso, <span>más simple.</span>
          </h2>
        </div>
        <p>
          De conocer tu vehículo a encontrar su solución.
          <br />
          Todo conectado, en un solo lugar.
        </p>
      </div>

      <div className="dm-steps">
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

/* ── Ecosistema de servicios ────────────────────────────────────────────── */
export function Ecosistema({ onIrServicios }) {
  return (
    <section className="dm-container dm-eco">
      <div className="dm-eco__art">
        <div className="dm-eco__orbit dm-eco__orbit--1" />
        <div className="dm-eco__orbit dm-eco__orbit--2" />
        <div className="dm-eco__orbit dm-eco__orbit--3" />
        <div className="dm-eco__center">
          <Icon name="tool" size={52} />
        </div>
        <span className="dm-eco__node dm-eco__node--1"><Icon name="car" size={21} /></span>
        <span className="dm-eco__node dm-eco__node--2"><Icon name="box" size={21} /></span>
        <span className="dm-eco__node dm-eco__node--3"><Icon name="check" size={21} /></span>
        <div className="dm-eco__label">
          UN ECOSISTEMA. TODO CONECTADO.
          <span>REPUESTO → SERVICIO → MOVIMIENTO</span>
        </div>
      </div>

      <div className="dm-eco__copy">
        <div className="dm-eyebrow">
          <span className="dm-line" /> MÁS ALLÁ DE LA PIEZA
        </div>
        <h2>
          ¿Necesitas más
          <br />
          que el repuesto?
        </h2>
        <p>
          La pieza es solo el comienzo. Desde tu vehículo puedes revisar
          repuestos compatibles, historial de mantenimiento y solicitar el
          servicio sin perder el contexto de lo que ya compraste.
        </p>
        <button className="dm-btn dm-btn--dark" onClick={onIrServicios}>
          Explorar mantenimiento <Icon name="arrow" size={18} />
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
      title: "Encuentra",
      text: "El repuesto correcto, sin perder tiempo en búsquedas interminables.",
    },
    {
      icon: "shield",
      title: "Comprueba",
      text: "La compatibilidad antes de comprar. Decisiones con información, no con dudas.",
    },
    {
      icon: "layers",
      title: "Compara",
      text: "Marcas y alternativas para elegir lo que tu vehículo realmente necesita.",
    },
    {
      icon: "sliders",
      title: "Gestiona",
      text: "Tus vehículos y tus pedidos desde una plataforma diseñada para conectarlo todo.",
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
            <span>Conectamos soluciones.</span>
          </h2>
        </div>
        <p>
          El repuesto correcto. La información clara.
          <br />
          La confianza para seguir avanzando.
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
          <span className="dm-eyebrow">TU CATÁLOGO EN MOVIMIENTO</span>
          <p>Datos reales del inventario disponible.</p>
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
export function CierreBanner({ onIrVehiculo }) {
  return (
    <section className="dm-cierre">
      <div className="dm-container">
        <div>
          <span className="dm-eyebrow">TU VEHÍCULO MERECE LA PIEZA CORRECTA.</span>
          <h2>Que nada detenga tu camino.</h2>
        </div>
        <button className="dm-btn dm-btn--primary" onClick={onIrVehiculo}>
          Encuentra tu repuesto <Icon name="arrow" size={19} />
        </button>
      </div>
    </section>
  );
}