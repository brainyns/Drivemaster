"""
Servidor HTTP de Inferencia y Explicabilidad (XAI) para DriveMaster ML
Compatible con Python estándar sin dependencias externas.
Proyecto de Aula: Brainy Blanco Hernandez, Sebastian Ortega Zarza, Juan Andres Cantillo Leon, Elias Daniel Puello Florez.
"""

import json
import math
import os
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PESOS_PATH = os.path.join(BASE_DIR, "modelo_pesos.json")

if not os.path.exists(PESOS_PATH):
    raise FileNotFoundError("modelo_pesos.json no encontrado. Ejecuta entrenar_modelo.py primero.")

with open(PESOS_PATH, "r", encoding="utf-8") as f:
    MODELO = json.load(f)

NUMERIC_FEATURES = MODELO["numeric_features"]
MEANS = MODELO["means"]
STDS = MODELO["stds"]
MARCAS = MODELO["marcas"]
SERVICIOS = MODELO["tipos_servicio"]
WEIGHTS = MODELO["weights"]
BIAS = MODELO["bias"]
OPTIMAL_THRESHOLD = MODELO.get("optimal_threshold", 0.40)
FEATURE_NAMES = MODELO.get("feature_names", [])

def sigmoide(z):
    z = max(-50.0, min(50.0, z))
    return 1.0 / (1.0 + math.exp(-z))

def construir_vector_caracteristicas(data):
    anio = float(data.get("anio_vehiculo", 2018))
    antiguedad = max(0.0, 2026.0 - anio)
    dist = float(data.get("distancia_km", 5.0))
    log_dist = math.log(max(dist, 0.1) + 1.0)
    cant_rep = float(data.get("cantidad_repuestos", 2))
    num_t = float(data.get("num_tiendas", 1))
    m_rep = float(data.get("monto_repuestos", 0.0))
    m_mo = float(data.get("monto_mano_obra", 0.0))
    subtotal = m_rep + m_mo
    tot = float(data.get("total_con_iva") or round(subtotal * 1.19, 2))
    prop_rep = round(m_rep / tot, 3) if tot > 0 else 0.0
    tiempo = float(data.get("tiempo_respuesta_horas", 2.0))
    log_tiempo = math.log(max(tiempo, 0.1) + 1.0)
    calif = float(data.get("calificacion_taller", 4.6))
    rep_dist = calif / (log_dist + 0.5)
    antig_precio = (antiguedad / 10.0) * (tot / 1000000.0)

    raw_nums = [
        antiguedad,
        dist,
        log_dist,
        cant_rep,
        num_t,
        m_rep,
        m_mo,
        tot,
        prop_rep,
        tiempo,
        log_tiempo,
        calif,
        rep_dist,
        antig_precio
    ]

    x_vec = []
    num_count = len(NUMERIC_FEATURES)
    for j in range(num_count):
        val_norm = (raw_nums[j] - MEANS[j]) / (STDS[j] if STDS[j] > 1e-9 else 1.0)
        x_vec.append(val_norm)

    marca_req = str(data.get("marca_vehiculo", "Chevrolet")).strip().lower()
    for m in MARCAS:
        x_vec.append(1.0 if marca_req == m.lower() else 0.0)

    servicio_req = str(data.get("tipo_servicio", "MANTENIMIENTO_PREVENTIVO")).strip().upper()
    for s in SERVICIOS:
        x_vec.append(1.0 if servicio_req == s.upper() else 0.0)

    info_contexto = {
        "antiguedad": antiguedad,
        "distancia_km": dist,
        "tiempo_horas": tiempo,
        "calificacion": calif,
        "total_con_iva": tot,
        "monto_repuestos": m_rep,
        "monto_mano_obra": m_mo,
        "num_tiendas": num_t,
        "marca": data.get("marca_vehiculo", "Chevrolet"),
        "servicio": data.get("tipo_servicio", "MANTENIMIENTO_PREVENTIVO")
    }

    return x_vec, info_contexto

def predecir_probabilidad(data):
    x_vec, ctx = construir_vector_caracteristicas(data)

    # Inferencia lineal + sigmoide
    contribuciones = []
    z = BIAS
    for j, (w, x) in enumerate(zip(WEIGHTS, x_vec)):
        contrib = w * x
        z += contrib
        nombre_feat = FEATURE_NAMES[j] if j < len(FEATURE_NAMES) else f"feat_{j}"
        contribuciones.append((nombre_feat, contrib, w, x))

    prob_aprob = round(sigmoide(z), 4)
    prob_rechazo = round(1.0 - prob_aprob, 4)

    # Clasificación con umbral óptimo calibrado
    if prob_aprob >= 0.70:
        nivel_riesgo = "BAJO"
        prediccion = "APROBADA"
    elif prob_aprob >= OPTIMAL_THRESHOLD:
        nivel_riesgo = "MEDIO"
        prediccion = "INCIERTA"
    else:
        nivel_riesgo = "ALTO"
        prediccion = "RECHAZADA"

    # Explicabilidad Matemática (XAI - Feature Contributions)
    factores_favor = []
    factores_riesgo = []

    # Ordenar por magnitud positiva y negativa
    favor_sorted = sorted([c for c in contribuciones if c[1] > 0.05], key=lambda c: c[1], reverse=True)
    riesgo_sorted = sorted([c for c in contribuciones if c[1] < -0.05], key=lambda c: c[1])

    def describir_factor(nombre, contrib, es_favor):
        impacto_pct = round(abs(contrib) * 10.0, 1)
        if "calificacion" in nombre:
            if es_favor:
                return "Reputación del taller", f"La alta calificación ({ctx['calificacion']} estrellas) genera gran confianza en el cliente.", impacto_pct
            return "Calificación del taller", f"La calificación ({ctx['calificacion']} estrellas) reduce la confianza frente a otros talleres.", impacto_pct
        if "tiempo" in nombre:
            if es_favor:
                return "Respuesta ágil", f"Respondiste en {ctx['tiempo_horas']:.1f}h (muy rápido). Maximiza el interés del cliente.", impacto_pct
            return "Tiempo de respuesta", f"Respuesta en {ctx['tiempo_horas']:.1f}h. Enviar antes de 4h eleva mucho la probabilidad.", impacto_pct
        if "antiguedad_x_precio" in nombre:
            if es_favor:
                return "Excelente relación costo/vehículo", f"Presupuesto muy proporcionado (${ctx['total_con_iva']:,.0f}) para un auto modelo {int(2026 - ctx['antiguedad'])}.", impacto_pct
            return "Relación vehículo / costo", f"El valor (${ctx['total_con_iva']:,.0f}) se percibe elevado para un vehículo de {int(ctx['antiguedad'])} años.", impacto_pct
        if "reputacion_vs_distancia" in nombre:
            if es_favor:
                return "Balance reputación y cercanía", "La reputación del taller compensa con creces la distancia.", impacto_pct
            return "Balance reputación y cercanía", "La distancia supera el atractivo de la reputación actual.", impacto_pct
        if "distancia" in nombre:
            if es_favor:
                return "Proximidad geográfica", f"El taller está a solo {ctx['distancia_km']:.1f} km del cliente.", impacto_pct
            return "Distancia geográfica", f"El cliente se encuentra a {ctx['distancia_km']:.1f} km de distancia.", impacto_pct
        if "monto_repuestos" in nombre or "monto_mano_obra" in nombre or "total" in nombre:
            if es_favor:
                return "Precio competitivo", f"Presupuesto de ${ctx['total_con_iva']:,.0f} atractivo y competitivo para el mercado.", impacto_pct
            return "Costo del servicio", f"El presupuesto total de ${ctx['total_con_iva']:,.0f} se percibe por encima de la media.", impacto_pct
        if "tiendas" in nombre:
            if es_favor:
                return "Logística optimizada", "Repuestos centralizados en un único proveedor.", impacto_pct
            return "Logística de repuestos", f"Requiere repuestos de {int(ctx['num_tiendas'])} tiendas diferentes.", impacto_pct
        
        # Categóricos (marcas o servicios)
        if nombre.startswith("marca_"):
            marca_nom = nombre.replace("marca_", "")
            if es_favor:
                return f"Afinidad con marca {marca_nom}", f"Los propietarios de {marca_nom} tienen alta tasa de aceptación en esta gama.", impacto_pct
            return f"Sensibilidad marca {marca_nom}", f"Propietarios de {marca_nom} suelen ser más comparativos con cotizaciones.", impacto_pct
        if nombre.startswith("servicio_"):
            serv_nom = nombre.replace("servicio_", "").replace("_", " ").title()
            if es_favor:
                return f"Demanda de {serv_nom}", f"Servicio con alta tasa de necesidad y aprobación en la plataforma.", impacto_pct
            return f"Sensibilidad en {serv_nom}", f"Servicio de alta inspección y comparación de precios por clientes.", impacto_pct

        tit = nombre.replace("_", " ").title()
        desc = "Impacto positivo en la decisión." if es_favor else "Impacto que reduce la probabilidad."
        return tit, desc, impacto_pct

    for f in favor_sorted[:3]:
        tit, desc, imp = describir_factor(f[0], f[1], True)
        factores_favor.append({
            "factor": f[0],
            "titulo": tit,
            "descripcion": desc,
            "impacto": imp,
            "tipo": "FAVOR"
        })

    for r in riesgo_sorted[:3]:
        tit, desc, imp = describir_factor(r[0], r[1], False)
        factores_riesgo.append({
            "factor": r[0],
            "titulo": tit,
            "descripcion": desc,
            "impacto": imp,
            "tipo": "RIESGO"
        })

    # Recomendaciones dinámicas accionables
    recs = []
    if ctx["tiempo_horas"] > 24.0:
        recs.append("El tiempo de respuesta fue mayor a 24 horas. Responder en las primeras 4 horas eleva la probabilidad significativamente.")
    elif ctx["tiempo_horas"] > 6.0:
        recs.append("Procura responder en menos de 3 horas para asegurar una tasa de conversión óptima.")
        
    if ctx["distancia_km"] > 18.0:
        recs.append(f"El cliente está a {ctx['distancia_km']:.1f} km. Ofrece facilidades de agenda, vehículo sustituto o recogida.")
        
    if ctx["antiguedad"] > 8 and ctx["total_con_iva"] > 800000.0:
        recs.append("Vehículo con más de 8 años: ofrece opciones de repuestos genéricos garantizados o financiación para evitar rechazo.")
    elif ctx["total_con_iva"] > 1500000.0:
        recs.append("Monto total elevado. Desglosa con claridad las garantías de repuestos y mano de obra para justificar la inversión.")
        
    if ctx["calificacion"] < 4.2:
        recs.append("Promueve que clientes satisfechos califiquen tu taller; cada 0.2 estrellas adicionales aumentan tus conversiones.")
        
    if prob_aprob >= 0.70:
        recs.append("Cotización con excelente perfil competitivo. Lista para emitir al cliente.")

    return {
        "probabilidad_aprobacion": prob_aprob,
        "probabilidad_rechazo": prob_rechazo,
        "prediccion": prediccion,
        "nivel_riesgo": nivel_riesgo,
        "score_logit": round(z, 4),
        "umbral_decision": OPTIMAL_THRESHOLD,
        "factores_favor": factores_favor,
        "factores_riesgo": factores_riesgo,
        "recomendaciones": recs
    }

class MLRequestHandler(BaseHTTPRequestHandler):
    def _set_headers(self, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_OPTIONS(self):
        self._set_headers(204)

    def do_GET(self):
        if self.path in ["", "/"]:
            self._set_headers(200)
            res = {
                "sistema": "DriveMaster ML API (XAI 2.0)",
                "estado": "ACTIVO",
                "proyecto_de_aula": "Modelo predictivo de probabilidad de aprobación de cotizaciones con explicabilidad",
                "integrantes": ["Brainy Blanco Hernandez", "Sebastian Ortega Zarza", "Juan Andres Cantillo Leon", "Elias Daniel Puello Florez"],
                "metricas": MODELO.get("metrics", {})
            }
            self.wfile.write(json.dumps(res, ensure_ascii=False).encode("utf-8"))
        elif self.path == "/metricas":
            self._set_headers(200)
            self.wfile.write(json.dumps(MODELO.get("metrics", {}), ensure_ascii=False).encode("utf-8"))
        else:
            self._set_headers(404)
            self.wfile.write(json.dumps({"error": "Ruta no encontrada"}).encode("utf-8"))

    def do_POST(self):
        if self.path in ["/predict", "/api/predict"]:
            try:
                content_length = int(self.headers.get("Content-Length", 0))
                body = self.rfile.read(content_length).decode("utf-8")
                data = json.loads(body) if body else {}
                resultado = predecir_probabilidad(data)
                self._set_headers(200)
                self.wfile.write(json.dumps(resultado, ensure_ascii=False).encode("utf-8"))
            except Exception as e:
                self._set_headers(500)
                self.wfile.write(json.dumps({"error": str(e)}).encode("utf-8"))
        else:
            self._set_headers(404)
            self.wfile.write(json.dumps({"error": "Endpoint no encontrado"}).encode("utf-8"))

def run_server(port=8000):
    server_address = ("127.0.0.1", port)
    httpd = HTTPServer(server_address, MLRequestHandler)
    print(f"Servidor DriveMaster ML iniciado en http://127.0.0.1:{port}")
    httpd.serve_forever()

if __name__ == "__main__":
    puerto = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    run_server(puerto)
