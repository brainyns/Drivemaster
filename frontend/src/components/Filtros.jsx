import "../css/filtros.css";

const CATEGORIAS = ["Todos los productos"];

const Filtros = ({ categoriaActiva, onCategoriaChange, soloMasVendidos, onMasVendidosChange, total }) => {
  return (
    <div className="filtros">
      <span className="filtros__label">Filtrar</span>
      <div className="filtros__separator" />

      <div className="filtros__grupo">
        {CATEGORIAS.map((cat) => (
          <button
            key={cat}
            className={`filtros__chip ${categoriaActiva === cat ? "active" : ""}`}
            onClick={() => onCategoriaChange(cat)}
          >
            {cat}
          </button>
        ))}
      </div>

      <div className="filtros__separator" />

      <button
        className={`filtros__chip filtros__chip--especial ${soloMasVendidos ? "active" : ""}`}
        onClick={() => onMasVendidosChange(!soloMasVendidos)}
      >
        🔥 Más vendidos
      </button>

      <span className="filtros__count">
        <span>{total}</span> productos encontrados
      </span>
    </div>
  );
};

export default Filtros;