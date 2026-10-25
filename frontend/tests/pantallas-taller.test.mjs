// Pantallas del rol TALLER: lógica pura de cada una y que usen el sistema compartido (components/ui).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { completitudPerfil, errorHorario, errorTarifa, iniciales } from "../src/pages/taller/perfilTaller.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

const COMPLETO = {
  latitud: 4.6, longitud: -74.1, horario: "Lunes a viernes", tarifaHora: 60000,
  telefono: "300 123 4567", direccion: "Cra 7 # 10-20", especialidades: ["Frenos"],
};

test("Mi taller: el perfil completo cuenta lo que existe en el backend y dice qué falta", () => {
  assert.deepEqual(completitudPerfil({ ...COMPLETO, logoUrl: "https://img/logo.png" }, 1), { porcentaje: 100, faltan: [] });
  const { porcentaje, faltan } = completitudPerfil({ ...COMPLETO, latitud: null, horario: "  " }, 0);
  assert.equal(porcentaje, 50);
  assert.deepEqual(faltan.map((f) => f.clave), ["ubicacion", "horario", "mecanicos", "logo"]);
  assert.ok(faltan.every((f) => f.seccion), "lo que el taller puede resolver lleva a su sección");
  const admin = completitudPerfil({ ...COMPLETO, logoUrl: "x", telefono: "" }, 1).faltan[0];
  assert.equal(admin.admin, true, "el teléfono lo registra el administrador");
});

test("Mi taller: la tarifa y el horario se validan en línea con los límites del backend", () => {
  assert.equal(errorTarifa(""), "Escribe la tarifa por hora.");
  assert.equal(errorTarifa("0"), "La tarifa debe ser mayor que 0.");
  assert.match(errorTarifa("1000001"), /no puede pasar/);
  assert.equal(errorTarifa("abc"), "Escribe solo números, sin puntos ni signos.");
  assert.equal(errorTarifa("65000"), null);
  assert.equal(errorHorario("x".repeat(200)), null);
  assert.match(errorHorario("x".repeat(201)), /200 caracteres/);
});

test("Mi taller: iniciales del avatar sin palabras de relleno", () => {
  assert.equal(iniciales("Mecánica Rápida"), "MR");
  assert.equal(iniciales("Taller El Pistón"), "P");
  assert.equal(iniciales(""), "T");
});

test("Mi taller edita por sección y solo con los endpoints que ya existen", () => {
  const pagina = leer("../src/pages/taller/TallerMiTaller.jsx");
  for (const seccion of ["mt-negocio", "mt-servicios", "mt-ubicacion", "mt-horario", "mt-contacto"]) {
    assert.ok(pagina.includes(`id="${seccion}"`), `existe la sección ${seccion}`);
  }
  assert.ok(pagina.includes("actualizarDatosTaller(token, { tarifaHora: Number(tarifa) })"), "la tarifa viaja sola");
  assert.ok(pagina.includes("guardarHorario(token, taller.id, franjasDesdeSemana(semana))"), "el horario viaja completo, por días");
  assert.ok(pagina.includes("<SelectorUbicacion"), "usa el selector de mapa existente");
  assert.ok(pagina.includes("<Calificacion rating={taller.rating} calificaciones={taller.calificaciones} />"),
    "la calificación real del DTO (promedio y cantidad)");
  assert.match(leer("../src/components/servicio/MecanicosTaller.jsx"), /<SeccionEditable/);
});

test("Inicio: lo urgente va primero y las citas de hoy son solo las de hoy", async () => {
  const { urgentes, citasDeHoy } = await import("../src/pages/taller/resumenTaller.js");
  const ahora = new Date(2026, 9, 19, 9, 0).getTime();
  const en = (h) => new Date(ahora + h * 3600000).toISOString();
  const lista = [
    { id: "lista", estado: "LISTA", fechaCreacion: en(-50) },
    { id: "cotizar", estado: "DIAGNOSTICADA", fechaCreacion: en(-20) },
    { id: "tarde", estado: "ASIGNADA_A_TALLER", aceptadaPorTaller: false, fechaLimiteRespuesta: en(10) },
    { id: "ya", estado: "ASIGNADA_A_TALLER", aceptadaPorTaller: false, fechaLimiteRespuesta: en(1) },
    { id: "cita", estado: "ASIGNADA_A_TALLER", aceptadaPorTaller: true, fechaRevision: en(5) },
    { id: "manana", estado: "ASIGNADA_A_TALLER", aceptadaPorTaller: true, fechaRevision: en(26) },
  ];
  const u = urgentes(lista, ahora);
  assert.deepEqual(u.map((x) => x.s.id), ["ya", "tarde", "cita", "cotizar", "lista"]);
  assert.equal(u[0].tono, "error", "menos de 2 h para responder es crítico");
  assert.equal(u[1].tono, "aviso");
  assert.ok(u.every((x) => x.estado && x.accion && x.pagina), "cada ítem dice su estado con texto y qué hacer");
  assert.deepEqual(citasDeHoy(lista, ahora).map((s) => s.id), ["cita"]);
});

test("Inicio usa el sistema compartido y la barra inferior en celular", () => {
  const pagina = leer("../src/pages/taller/TallerInicio.jsx");
  for (const pieza of ["<EncabezadoPagina", "<Indicador", "<ChipEstado", "<Alerta", "<EstadoVacio", "<Esqueleto", "<BarraAccionMovil"]) {
    assert.ok(pagina.includes(pieza), `usa ${pieza}`);
  }
  assert.ok(!pagina.includes("→"), "sin flechas de texto: el ícono es SVG");
});

test("Solicitudes y Trabajos: filtros con chips, búsqueda con etiqueta y estados compartidos", () => {
  const pagina = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(pagina.includes('className="ui-chip" aria-pressed={filtro === f.key}'), "filtros con ui-chip");
  assert.ok(pagina.includes('<label htmlFor="st-busqueda" className="ui-sr">'), "la búsqueda tiene etiqueta");
  for (const pieza of ["<EstadoVacio", "<Esqueleto", "onReintentar={reintentar}", "<Pasos pasos={PASOS_TRABAJO}"]) {
    assert.ok(pagina.includes(pieza), `usa ${pieza}`);
  }
});

test("Detalle de solicitud: título h1, volver, error de la cita en su campo y barra inferior", () => {
  const pagina = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(pagina.includes("<EncabezadoPagina titulo={s.vehiculoDescripcion} onVolver={cerrarDetalle}"), "cabecera con volver");
  assert.ok(pagina.includes('<Campo id="ss-fecha-revision" label="Día de la revisión" error={errorDia || (!fechaCita && errorFecha)}'));
  assert.ok(pagina.includes('<Campo id="ss-hora-revision" label="Hora" error={fechaCita && !errorDia && !horaCita ? errorFecha : null}>'));
  assert.ok(pagina.includes("<BarraAccionMovil etiqueta={barra.etiqueta}"), "acción principal en la barra del celular");
  assert.ok(!pagina.includes("← "), "volver sin flecha de texto");
  assert.ok(pagina.includes("aceptarSolicitudServicio(token, seleccionada.id, {"), "misma llamada para aceptar");
});

test("Informes: período con chips, estrellas dibujadas y tarjetas compartidas", () => {
  const pagina = leer("../src/pages/taller/TallerInformes.jsx");
  assert.ok(pagina.includes('className="ui-chip" aria-pressed={meses === m}'));
  assert.ok(pagina.includes("<Estrellas") && !pagina.includes("★"), "sin estrellas de texto");
  assert.ok(pagina.includes("onReintentar={() => setIntento((n) => n + 1)}"), "el error se puede reintentar");
  assert.ok(pagina.includes('className="ui-card ti-tarjeta"'));
});

test("Órdenes anteriores: cabecera, chips compartidos y sin componentes definidos dentro del render", () => {
  const pagina = leer("../src/pages/taller/OrdenesAnteriores.jsx");
  assert.match(pagina, /<EncabezadoPagina\s+titulo="Órdenes anteriores"/);
  assert.ok(pagina.includes("catalogo={ESTADOS_ORDEN_ANTERIOR}"), "estado con el catálogo compartido");
  assert.ok(!/^ {2}function \w+\(/m.test(pagina), "los componentes no se crean en cada render");
  for (const llamada of ["confirmarPagoOrden(token, id)", "iniciarServicio(token, id)", "programarCita(token, id, fechasCita[id])"]) {
    assert.ok(pagina.includes(llamada), `misma llamada: ${llamada}`);
  }
  assert.doesNotMatch(leer("../src/css/panel-taller.css"), /border-left:\s*[2-9]px/);
});

test("Calificación: sin calificaciones es Nuevo (no un 5.0) y con ellas el promedio con su cantidad", async () => {
  const { datosCalificacion } = await import("../src/utils/calificacion.js");
  assert.equal(datosCalificacion(null, 0).nuevo, true);
  assert.equal(datosCalificacion(5, 0).nuevo, true, "un 5.0 sin calificaciones no cuenta");
  assert.equal(datosCalificacion(null, 0).texto, "Sin calificaciones aún");
  const d = datosCalificacion(4.25, 3);
  assert.equal(d.nuevo, false);
  assert.equal(d.promedio, "4,3");
  assert.equal(d.texto, "3 calificaciones");
  assert.equal(datosCalificacion(4, 1).texto, "1 calificación");
});

test("Servicios (admin): la calificación promedio usa la misma regla que el chip de cada taller", async () => {
  const { promedioDeTalleres } = await import("../src/utils/calificacion.js");
  // La lista del ADMIN (TallerResumen) antes llegaba sin calificaciones: cada fila decía "Nuevo" y el promedio no
  assert.equal(promedioDeTalleres([{ rating: 4.5 }, { rating: 5 }]), null, "sin cantidad, ningún taller cuenta");
  assert.equal(promedioDeTalleres([{ rating: 4.5, calificaciones: 2 }, { rating: 5, calificaciones: 0 }, { rating: null, calificaciones: 0 }]), 4.5);
  assert.equal(promedioDeTalleres([{ rating: 4, calificaciones: 1 }, { rating: 5, calificaciones: 3 }]), 4.5);
  assert.equal(promedioDeTalleres([]), null);
  assert.ok(leer("../src/pages/AdminTalleres.jsx").includes("promedioDeTalleres(talleres)"));
});

test("El admin ya no escribe la calificación a mano", () => {
  const admin = leer("../src/pages/AdminTalleres.jsx");
  assert.ok(!admin.includes("Rating inicial") && !admin.includes("form.rating"));
  assert.ok(admin.includes("<Calificacion rating={t.rating} calificaciones={t.calificaciones} compacta />"));
});

test("La vista previa de Mi taller usa los mismos campos que el mapa del cliente", async () => {
  const { comoLoVeElCliente } = await import("../src/pages/taller/perfilTaller.js");
  const vista = comoLoVeElCliente({
    id: "t1", nombre: "AutoMax", direccion: "Cra 1", ciudad: "Cali", telefono: "300", latitud: 1, longitud: 2,
    horario: "L-V 8-18", especialidades: ["Frenos"], rating: 4.5, calificaciones: 8,
    correoUsuario: "dueno@demo.com", tarifaHora: 60000, mecanicos: [{ nombre: "Carlos" }],
  });
  assert.deepEqual(Object.keys(vista), ["id", "nombre", "direccion", "ciudad", "telefono", "latitud", "longitud",
    "horario", "especialidades", "calificacion", "calificaciones", "logoUrl"], "las mismas llaves de TallerMapaDTO");
  assert.equal(vista.calificacion, 4.5);
  assert.deepEqual(comoLoVeElCliente({ id: "n" }).calificaciones, 0);
  const mapa = leer("../src/components/mapa/MapaTalleres.jsx");
  assert.equal((mapa.match(/<DatosTallerCliente/g) || []).length, 2, "en la lista y en el popup");
  assert.ok(leer("../src/pages/taller/TallerMiTaller.jsx").includes("<DatosTallerCliente taller={taller} />"));
});

test("Desde Inicio se abre la solicitud concreta y Volver regresa a Inicio, sin router", async () => {
  const { destinoSeccion } = await import("../src/pages/taller/resumenTaller.js");
  assert.deepEqual(destinoSeccion("agendadas"), { filtro: "agendadas", abrir: null, volverA: null }, "el menú y los avisos siguen igual");
  assert.deepEqual(destinoSeccion(null), { filtro: undefined, abrir: null, volverA: null });
  assert.deepEqual(destinoSeccion({ filtro: "responder", abrir: "s1", volverA: "taller-inicio" }),
    { filtro: "responder", abrir: "s1", volverA: "taller-inicio" });

  const inicio = leer("../src/pages/taller/TallerInicio.jsx");
  assert.ok(inicio.includes('irA(u.pagina, { filtro: u.filtro, abrir: u.s.id, volverA: "taller-inicio" })'));
  const solicitudes = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(solicitudes.includes("useState(abrirInicial)"), "abre el detalle directamente");
  assert.ok(solicitudes.includes('textoAfuera = "Inicio"') && solicitudes.includes("textoVolver={desdeAfuera ? textoAfuera : textos.volver}"),
    "Volver nombra la página de origen (Inicio, o Agenda si se abrió desde allá)");
  assert.ok(!leer("../src/App.jsx").includes("react-router"), "sin router nuevo");
});

test("Logo del taller: mismas reglas que el backend e iniciales de respaldo en todos lados", async () => {
  const { errorLogo } = await import("../src/pages/taller/perfilTaller.js");
  assert.equal(errorLogo({ type: "image/png", size: 1000 }), null);
  assert.equal(errorLogo({ type: "image/jpeg", size: 5 * 1024 * 1024 }), null);
  assert.match(errorLogo({ type: "image/svg+xml", size: 10 }), /JPG, PNG, WEBP o GIF/);
  assert.match(errorLogo({ type: "application/pdf", size: 10 }), /JPG, PNG, WEBP o GIF/);
  assert.match(errorLogo({ type: "image/png", size: 5 * 1024 * 1024 + 1 }), /menos de 5 MB/);
  assert.equal(errorLogo(null), "Elige una imagen.");

  const logo = leer("../src/components/ui/LogoNegocio.jsx");
  assert.ok(logo.includes("iniciales(nombre)") && logo.includes("onError"), "sin logo o si no carga: iniciales");
  assert.ok(leer("../src/pages/taller/TallerMiTaller.jsx").includes("subirLogoTaller(token, archivo)"));
  assert.equal((leer("../src/components/mapa/MapaTalleres.jsx").match(/<LogoNegocio/g) || []).length, 2, "lista y popup");
});

test("Solicitudes: estado vacío neutro y compacto, contador en todas las pestañas y enlace a lo que falta cotizar", () => {
  const p = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(!p.includes('icono: "message"'), "sin el globo de chat (parecido al de WhatsApp)");
  assert.ok(p.includes('icono: "inbox"'));
  assert.ok(p.includes('<span className="ui-chip__contador">{conteo[f.key]}</span>'), "el contador sale también en 0");
  assert.ok(p.includes("compacto>") && p.includes("conteo.diagnosticadas > 0"), "el enlace sale solo si hay algo por cotizar");
  assert.ok(leer("../src/css/ui.css").includes(".ui-vacio--compacto"));

  const menu = leer("../src/components/Sidebar.jsx");
  assert.match(menu, /label: "Solicitudes", icon: "bandeja"/);
  assert.match(menu, /label: "Mi taller",\s+icon: "garaje"/);
});

test("Horario por días: mismas reglas que el backend, 12 h cada 30 min, copiar el lunes y el cuerpo del PUT", async () => {
  const h = await import("../src/pages/taller/horarioTaller.js");
  assert.equal(h.OPCIONES_HORA.length, 48);
  assert.deepEqual(h.OPCIONES_HORA[0], { valor: "00:00", texto: "12:00 a. m." });
  assert.equal(h.hora12("14:30"), "2:30 p. m.");
  assert.equal(h.hora12("12:00"), "12:00 p. m.");

  const dia = (franjas, abierto = true) => ({ dia: "LUNES", abierto, franjas });
  assert.equal(h.errorDia(dia([{ apertura: "08:00", cierre: "12:00" }, { apertura: "12:00", cierre: "18:00" }])), null);
  assert.match(h.errorDia(dia([{ apertura: "18:00", cierre: "08:00" }])), /cierre debe ser después/);
  assert.match(h.errorDia(dia([{ apertura: "08:00", cierre: "13:00" }, { apertura: "12:00", cierre: "18:00" }])), /se cruzan/);
  assert.equal(h.errorDia(dia([], false)), null, "un día cerrado no tiene nada que validar");

  const semana = h.semanaPorDefecto().map((d) => (d.dia === "LUNES" ? { ...d, franjas: [{ apertura: "07:00", cierre: "17:00" }] } : d));
  const copiada = h.copiarLunesAHabiles(semana);
  assert.ok(["MARTES", "MIERCOLES", "JUEVES", "VIERNES"].every((c) => copiada.find((d) => d.dia === c).franjas[0].apertura === "07:00"));
  assert.equal(copiada.find((d) => d.dia === "SABADO").franjas[0].cierre, "13:00", "el sábado no cambia");

  const cuerpo = h.franjasDesdeSemana(copiada);
  assert.deepEqual(cuerpo.find((f) => f.diaSemana === "DOMINGO"), { diaSemana: "DOMINGO", abierto: false, horaApertura: null, horaCierre: null, orden: 0 });
  assert.deepEqual(h.semanaDesdeFranjas(cuerpo), copiada, "ida y vuelta sin perder nada");
  assert.equal(h.semanaDesdeFranjas([]), null, "sin horario configurado");

  const p = leer("../src/pages/taller/TallerMiTaller.jsx");
  assert.ok(!p.includes("<textarea"), "el horario ya no es texto libre");
  assert.ok(p.includes("Copiar lunes a todos los días hábiles") && p.includes('textoGuardar="Guardar horario"'));
});

test("Agenda: rejilla del mes de lunes a domingo, semana del celular y citas agrupadas por día local", async () => {
  const c = await import("../src/pages/taller/calendario.js");
  const rejilla = c.rejillaMes(new Date(2026, 9, 15));
  assert.equal(rejilla.length, 6);
  assert.equal(c.claveDia(rejilla[0][0]), "2026-09-28", "octubre de 2026 empieza en jueves: la rejilla arranca el lunes anterior");
  assert.ok(rejilla.every((s) => s[0].getDay() === 1 && s[6].getDay() === 0));
  assert.deepEqual(c.semanaDe(new Date(2026, 9, 24)).map(c.claveDia), [
    "2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23", "2026-10-24", "2026-10-25"]);
  assert.equal(c.tituloSemana(c.semanaDe(new Date(2026, 9, 1))), "28 de septiembre al 4 de octubre");
  assert.equal(c.tituloMes(new Date(2026, 9, 1)), "octubre de 2026");

  const porDia = c.citasPorDia([
    { id: "b", fechaRevision: "2026-10-20T15:00:00" },
    { id: "a", fechaRevision: "2026-10-20T08:30:00" },
    { id: "x", fechaRevision: null },
  ]);
  assert.deepEqual(Object.keys(porDia), ["2026-10-20"]);
  assert.deepEqual(porDia["2026-10-20"].map((x) => x.id), ["a", "b"]);

  const h = await import("../src/pages/taller/horarioTaller.js");
  const semana = h.semanaPorDefecto();
  assert.equal(h.abreEseDia(semana, new Date(2026, 9, 25)), false, "el domingo cierra");
  assert.equal(h.abreEseDia(null, new Date(2026, 9, 25)), true, "sin horario, todos abiertos");

  const inicio = leer("../src/pages/taller/TallerInicio.jsx");
  assert.equal((inicio.match(/irA\("taller-agenda"\)/g) || []).length, 3, "Ver agenda (cabecera y widget) y Citas de hoy");
  assert.ok(leer("../src/App.jsx").includes('"taller-agenda":      "agenda"'));
  const agenda = leer("../src/pages/taller/TallerAgenda.jsx");
  assert.ok(agenda.includes('volverA: "taller-agenda"') && agenda.includes("(max-width: 767px)"));
});

test("Cita de revisión: solo días abiertos y horas dentro de las franjas (la hora de cierre no cuenta)", async () => {
  const h = await import("../src/pages/taller/horarioTaller.js");
  const semana = h.semanaPorDefecto(); // L-V 8–12 y 2–6, sábado 8–1, domingo cerrado
  const antes = new Date(2026, 0, 1, 9, 0);
  const lunes = h.horasDeCita(semana, "2026-10-26", antes);
  assert.equal(lunes[0], "08:00");
  assert.ok(lunes.includes("11:30") && !lunes.includes("12:00") && !lunes.includes("13:30") && lunes.includes("14:00"));
  assert.equal(lunes.at(-1), "17:30", "a las 6:00 p. m. ya cerró");
  assert.equal(h.errorDiaCita(semana, "2026-10-25", antes), "Tu taller está cerrado los domingos. Elige otro día.");
  assert.equal(h.errorDiaCita(semana, "2026-10-31", antes), null, "el sábado abre");

  const hoyTarde = new Date(2026, 9, 26, 17, 45);
  assert.deepEqual(h.horasDeCita(semana, "2026-10-26", hoyTarde), [], "hoy, solo las horas que no han pasado");
  assert.match(h.errorDiaCita(semana, "2026-10-26", hoyTarde), /Ya no quedan horas/);
  assert.equal(h.horasDeCita(null, "2026-10-25", antes).length, 48, "sin horario configurado, cualquier hora");

  const p = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(!p.includes('type="datetime-local"'), "ya no se escribe cualquier fecha y hora");
  assert.ok(p.includes("fechaRevision: `${fechaCita}T${horaCita}`"));
});

test("Inicio avisa si falta el horario por días y lleva a editarlo en Mi taller", () => {
  const inicio = leer("../src/pages/taller/TallerInicio.jsx");
  assert.ok(inicio.includes("setSinHorario(!filas?.length)"), "desaparece cuando ya hay horario guardado");
  assert.ok(inicio.includes("Configura tu horario de atención para que tus citas se agenden en horas que sí puedes cumplir"));
  assert.ok(inicio.includes('irA("taller-mitaller", "horario")'));
  assert.ok(leer("../src/pages/taller/TallerMiTaller.jsx").includes('useState(abrirSeccion === "horario" ? "horario" : null)'));
  assert.ok(leer("../src/pages/PanelTaller.jsx").includes("abrirSeccion={destino.filtro}"));
});

test("Panel del taller: la bandeja y el taller se piden una vez en PanelTaller y llegan a Solicitudes y Trabajos", () => {
  const solicitudes = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(!solicitudes.includes("bandejaSolicitudesTaller") && !solicitudes.includes("miTaller("), "Solicitudes no los vuelve a pedir");
  assert.ok(solicitudes.includes("onReemplazar?.(actualizada)"));
  const panel = leer("../src/pages/PanelTaller.jsx");
  assert.equal((panel.match(/\{\.\.\.bandeja\}/g) || []).length, 2, "Solicitudes y Trabajos");
});

test("Solicitudes: el día de la semana sale de la fecha real, no de un texto fijo", async () => {
  const { formatFechaHora } = await import("../src/components/servicio/estadosServicio.js");
  const dia = (f) => new Date(f).toLocaleDateString("es-CO", { weekday: "short" });
  for (const f of ["2026-10-19T09:00:00", "2026-10-22T15:30:00", "2026-10-25T18:00:00"]) {
    assert.ok(formatFechaHora(f).startsWith(dia(f)), `${f} empieza por ${dia(f)}`);
  }
  assert.notEqual(dia("2026-10-19T09:00:00"), dia("2026-10-22T15:30:00"));
});

test("Solicitudes: toda la tarjeta abre el detalle y lo dice con \"Ver solicitud\"", () => {
  const pagina = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(pagina.includes('className="ss-tarjeta st-tarjeta st-tarjeta--boton" onClick={() => abrir(s)}'));
  assert.ok(pagina.includes("Ver solicitud <Icon"));
});

test("Trabajos: el historial del taller va resumido, sin su propio nombre y con el mecánico solo cuando cambia", () => {
  const pagina = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(pagina.includes("<LineaTiempo eventos={s.eventos} catalogo={ESTADOS_TALLER} vistaTaller resumida />"));
  const linea = leer("../src/components/servicio/LineaTiempo.jsx");
  assert.ok(linea.includes('vistaTaller && e.actorRol === "TALLER" ? null'), "el taller no se ve a sí mismo en cada evento");
  assert.ok(linea.includes("if (m[1] !== mecanicoAnterior) mecanico = m[1];"), "el mecánico solo si cambia");
  assert.ok(linea.includes("Ver historial ("), "botón para el historial completo");
});

test("Informes: barras con el color de su fase, vacías en 0, sin respuesta repetida y diagnóstico sin costo", () => {
  const pagina = leer("../src/pages/taller/TallerInformes.jsx");
  assert.ok(pagina.includes("{n > 0 && <span className=\"ti-barras__barra\""), "una barra en 0 no pinta nada");
  assert.ok(pagina.includes("faseDe(estado)]"), "cada estado con el color de su fase");
  assert.ok(!pagina.includes('titulo="Respuesta promedio"'), "el promedio queda solo en Tiempo de respuesta");
  assert.ok(pagina.includes("Sin costo:"));
});

test("Informes: el tiempo de respuesta llega en minutos y se muestra en min, h o d", async () => {
  const { duracionMinutos } = await import("../src/utils/formato.js");
  assert.equal(duracionMinutos(null), "—");
  assert.equal(duracionMinutos(0.4), "< 1 min");
  assert.equal(duracionMinutos(2.5), "3 min");
  assert.equal(duracionMinutos(45), "45 min");
  assert.equal(duracionMinutos(180), "3 h");
  assert.equal(duracionMinutos(200), "3 h 20 min");
  assert.equal(duracionMinutos(1680), "1 d 4 h");
  assert.equal(duracionMinutos(2880), "2 d");
  assert.equal(duracionMinutos(2870), "2 d");
  const pagina = leer("../src/pages/taller/TallerInformes.jsx");
  assert.ok(pagina.includes("duracionMinutos(tiempo.minutosPromedio)") && !pagina.includes("horasPromedio"));
  assert.ok(pagina.includes("Total histórico"), "los ingresos dicen que no dependen del periodo");
});

test("Inicio: tarjetas de estado con el color de su fase y una propia para lo listo para entregar", () => {
  const pagina = leer("../src/pages/taller/TallerInicio.jsx");
  for (const fase of ["nueva", "cotizacion", "curso", "lista"]) assert.ok(pagina.includes(`fase="${fase}"`), fase);
  assert.ok(pagina.includes('titulo="Listos para entregar" valor={datos.listos.length}'));
  assert.ok(pagina.includes('s.estado !== "LISTA"'), "lo listo no se cuenta también como en curso");
});

test("Trabajos: un botón con el siguiente paso de cada etapa y los montos de una sola petición", () => {
  const pagina = leer("../src/pages/TallerSolicitudes.jsx");
  for (const paso of ["Marcar repuestos recibidos", "Iniciar instalación", "Registrar pruebas", "Marcar como lista", "Entregar"]) {
    assert.ok(pagina.includes(`texto: "${paso}"`), paso);
  }
  assert.ok(!pagina.includes('s.estado === "ENTREGADA") return {'), "entregados sin botón");
  assert.equal((pagina.match(/cotizacionesDelTaller\(token\)/g) || []).length, 1, "una sola petición, no una por trabajo");
  assert.ok(pagina.includes("cotizacionesPorId[s.cotizacionId]"), "cada trabajo toma su cotización por id");
});
