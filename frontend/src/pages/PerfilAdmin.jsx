import { useCallback, useEffect, useState } from "react";
import { getToken, getUser } from "../services/authService";
import { getPerfil, cambiarPassword } from "../services/perfilService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import LogoNegocio from "../components/ui/LogoNegocio";
import Indicador from "../components/ui/Indicador";
import Alerta from "../components/ui/Alerta";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import CampoClave from "../components/ui/CampoClave";
import Dato from "../components/ui/Dato";
import Icon from "../components/public/Icon";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/perfil-admin.css";

const ROL_LABELS = {
  SUPERADMIN: "Superadmin",
  ADMIN: "Administrador",
  VENDEDOR: "Vendedor",
};

function formatCOP(valor) {
  if (valor == null) return "$0";
  return new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(valor);
}

function formatFecha(iso) {
  if (!iso) return "No disponible";
  const d = new Date(iso);
  return d.toLocaleDateString("es-CO", {
    year: "numeric", month: "long", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;

export default function PerfilAdmin() {
  const token = getToken();
  const user = getUser();
  const [perfil, setPerfil] = useState(null);
  const [loading, setLoading] = useState(true);
  const [reintentando, setReintentando] = useState(false);

  const [passActual, setPassActual] = useState("");
  const [passNueva, setPassNueva] = useState("");
  const [passConfirm, setPassConfirm] = useState("");
  const [passMsg, setPassMsg] = useState(null);
  const [passError, setPassError] = useState(null);
  const [errores, setErrores] = useState({}); // { nueva, confirmar }
  const [guardando, setGuardando] = useState(false);

  const cargar = useCallback(() => {
    if (!token) return Promise.resolve();
    return getPerfil(token)
      .then(setPerfil)
      .catch(() => setPerfil(null))
      .finally(() => setLoading(false));
  }, [token]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setPassMsg(null);
    setPassError(null);
    setErrores({});
    if (passNueva !== passConfirm) {
      setErrores({ confirmar: "Las contraseñas no coinciden" });
      return;
    }
    if (passNueva.length < 6) {
      setErrores({ nueva: "La nueva contraseña debe tener al menos 6 caracteres" });
      return;
    }
    setGuardando(true);
    try {
      const res = await cambiarPassword(token, passActual, passNueva);
      setPassMsg(res.message || "Contraseña actualizada");
      setPassActual(""); setPassNueva(""); setPassConfirm("");
      setTimeout(() => setPassMsg(null), 3000);
    } catch (err) {
      setPassError(err.message);
    } finally {
      setGuardando(false);
    }
  };

  if (loading) {
    return (
      <div className="ss-panel--plataforma pp-root pfa-root">
        <EncabezadoPagina titulo="Mi perfil" descripcion="Información personal y actividad en el sistema." />
        <Esqueleto variante="formulario" cantidad={4} texto="Cargando perfil…" />
      </div>
    );
  }
  if (!perfil) {
    return (
      <div className="ss-panel--plataforma pp-root pfa-root">
        <EncabezadoPagina titulo="Mi perfil" descripcion="Información personal y actividad en el sistema." />
        <Alerta onReintentar={reintentar} reintentando={reintentando}>No se pudo cargar el perfil.</Alerta>
      </div>
    );
  }

  const stats = perfil.stats || {};
  const isAdminOrSuper = user?.rol === "ADMIN" || user?.rol === "SUPERADMIN";
  const isVendedor = user?.rol === "VENDEDOR";
  const isSuper = user?.rol === "SUPERADMIN";
  const rol = ROL_LABELS[perfil.rol] || perfil.rol;

  return (
    <div className="ss-panel--plataforma pp-root pfa-root">
      <EncabezadoPagina titulo="Mi perfil" descripcion="Información personal y actividad en el sistema." />

      <div className="pfa-columnas pp-seccion">
        <section className="ui-seccion" aria-labelledby="pfa-personal">
          <div className="ui-seccion__cabecera">
            <LogoNegocio nombre={perfil.nombre} tamano="mediano" />
            <div className="ui-seccion__texto">
              <h2 id="pfa-personal" className="ui-seccion__titulo">{perfil.nombre || "Sin nombre"}</h2>
              {rol && <p className="ui-seccion__desc"><ChipEstado tono="acento">{rol}</ChipEstado></p>}
            </div>
          </div>
          <dl className="ui-datos">
            <Dato etiqueta="Correo electrónico" ancho>{perfil.correo}</Dato>
            <Dato etiqueta="Rol del sistema">{rol}</Dato>
            <Dato etiqueta="Miembro desde">{formatFecha(perfil.fechaCreacion)}</Dato>
            <Dato etiqueta="Último acceso">{formatFecha(perfil.ultimoLogin)}</Dato>
          </dl>
        </section>

        <section className="ui-seccion" aria-labelledby="pfa-seguridad">
          <div className="ui-seccion__cabecera">
            <span className="ui-seccion__icono" aria-hidden="true"><Icon name="lock" size={18} /></span>
            <div className="ui-seccion__texto">
              <h2 id="pfa-seguridad" className="ui-seccion__titulo">Cambiar contraseña</h2>
              <p className="ui-seccion__desc">La nueva debe tener al menos 6 caracteres.</p>
            </div>
          </div>
          <form onSubmit={handleChangePassword} className="ui-form">
            <Campo id="pfa-actual" label="Contraseña actual">
              <CampoClave autoComplete="current-password" required value={passActual} onChange={(e) => setPassActual(e.target.value)} />
            </Campo>
            <Campo id="pfa-nueva" label="Nueva contraseña" error={errores.nueva}>
              <CampoClave autoComplete="new-password" required value={passNueva}
                onChange={(e) => { setPassNueva(e.target.value); if (errores.nueva) setErrores({}); }} />
            </Campo>
            <Campo id="pfa-confirmar" label="Confirmar nueva contraseña" error={errores.confirmar}>
              <CampoClave autoComplete="new-password" required value={passConfirm}
                onChange={(e) => { setPassConfirm(e.target.value); if (errores.confirmar) setErrores({}); }} />
            </Campo>
            {passMsg && <Alerta tono="ok">{passMsg}</Alerta>}
            {passError && <Alerta>{passError}</Alerta>}
            <div className="ui-form-pie">
              <button type="submit" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
                {guardando ? "Actualizando…" : "Actualizar contraseña"}
              </button>
            </div>
          </form>
        </section>
      </div>

      <section aria-labelledby="pfa-actividad">
        <h2 id="pfa-actividad" className="pp-titulo">Actividad</h2>
        <div className="ui-indicadores pp-seccion">
          {isAdminOrSuper && (
            <>
              <Indicador titulo="Ventas realizadas" valor={stats.totalVentas || 0} />
              <Indicador titulo="Solicitudes aprobadas" valor={stats.solicitudesAprobadas || 0} />
              <Indicador titulo="Total generado" valor={formatCOP(stats.totalIngresos)} />
              <Indicador titulo="Productos agregados" valor={stats.productosAgregados || stats.totalProductos || 0} />
            </>
          )}
          {isVendedor && (
            <>
              <Indicador titulo="Clientes atendidos" valor={stats.clientesAtendidos || 0} />
              <Indicador titulo="Ventas del día" valor={stats.ventasDelDia || 0} />
              <Indicador titulo="Ventas realizadas" valor={stats.ventasRealizadas || 0} />
            </>
          )}
          {isSuper && <Indicador icono="shield" titulo="Staff del sistema" valor={stats.totalUsuariosStaff || 0} />}
        </div>
        <dl className="ui-datos pfa-secundarios">
          <Dato etiqueta="Stock bajo">{plural(stats.stockBajo || 0, "producto", "productos")}</Dato>
          <Dato etiqueta="Solicitudes pendientes">{plural(stats.solicitudesPendientes || 0, "solicitud", "solicitudes")}</Dato>
          <Dato etiqueta="Clientes registrados">{plural(stats.totalClientes || 0, "cliente", "clientes")}</Dato>
        </dl>
      </section>
    </div>
  );
}
