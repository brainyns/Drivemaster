"""
Servidor HTTP de Inferencia para DriveMaster ML
Compatible con Python estándar sin dependencias externas.
Proyecto de Aula: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello.
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

def sigmoide(z):
    z = max(-50.0, min(50.0, z))
    return 1.0 / (1.0 + math.exp(-z))

def predecir_probabilidad(data):
    # Valores numéricos
    monto_repuestos = float(data.get("monto_repuestos", 0.0))
    monto_mano_obra = float(data.get("monto_mano_obra", 0.0))
    subtotal = monto_repuestos + monto_mano_obra
    total_con_iva = float(data.get("total_con_iva") or round(subtotal * 1.19, 2))
    prop_rep = round(monto_repuestos / total_con_iva, 3) if total_con_iva > 0 else 0.0

    raw_nums = [
        float(data.get("anio_vehiculo", 2018)),
        float(data.get("distancia_km", 5.0)),
        float(data.get("cantidad_repuestos", 2)),
        float(data.get("num_tiendas", 1)),
        monto_repuestos,
        monto_mano_obra,
        total_con_iva,
        prop_rep,
        float(data.get("tiempo_respuesta_horas", 2.0)),
        float(data.get("calificacion_taller", 4.6))
    ]

    # Normalizar numéricos
    x_vec = []
    for j in range(len(raw_nums)):
        val_norm = (raw_nums[j] - MEANS[j]) / STDS[j]
        x_vec.append(val_norm)

    # One-hot categóricos
    marca_req = str(data.get("marca_vehiculo", "Chevrolet"))
    for m in MARCAS:
        x_vec.append(1.0 if marca_req.strip().lower() == m.lower() else 0.0)

    servicio_req = str(data.get("tipo_servicio", "FRENOS"))
    for s in SERVICIOS:
        x_vec.append(1.0 if servicio_req.strip().upper() == s.upper() else 0.0)

    # Inferencia producto punto + bias
    z = BIAS + sum(w * x for w, x in zip(WEIGHTS, x_vec))
    prob_aprob = round(sigmoide(z), 4)
    prob_rechazo = round(1.0 - prob_aprob, 4)

    if prob_aprob >= 0.70:
        nivel_riesgo = "BAJO"
        prediccion = "APROBADA"
    elif prob_aprob >= 0.45:
        nivel_riesgo = "MEDIO"
        prediccion = "INCIERTA"
    else:
        nivel_riesgo = "ALTO"
        prediccion = "RECHAZADA"

    # Generar recomendaciones dinámicas
    recs = []
    tiempo_h = raw_nums[8]
    dist_km = raw_nums[1]
    calif = raw_nums[9]

    if tiempo_h > 24.0:
        recs.append("El tiempo de respuesta fue alto (> 24h). Enviar la cotización en las primeras 4 horas eleva la probabilidad hasta un 35%.")
    if dist_km > 15.0:
        recs.append(f"El cliente está a {dist_km:.1f} km del taller. Ofrece facilidades de cita o inspección rápida.")
    if total_con_iva > 1200000.0:
        recs.append("El monto total es elevado. Considera ofrecer repuestos alternativos o formas de pago.")
    if calif < 4.2:
        recs.append("La calificación del taller impacta la decisión del cliente. Promueve buenas calificaciones.")
    if prob_aprob >= 0.70:
        recs.append("Cotización atractiva y con alta expectativa de éxito según el perfil del auto.")

    return {
        "probabilidad_aprobacion": prob_aprob,
        "probabilidad_rechazo": prob_rechazo,
        "prediccion": prediccion,
        "nivel_riesgo": nivel_riesgo,
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
        if self.path == "/":
            self._set_headers(200)
            res = {
                "sistema": "DriveMaster ML API",
                "estado": "ACTIVO",
                "proyecto_de_aula": "Modelo predictivo de probabilidad de aprobación de cotizaciones",
                "integrantes": ["Brainy Blanco", "Sebastian Ortega", "Juan Cantillo", "Elias Puello"]
            }
            self.wfile.write(json.dumps(res).encode("utf-8"))
        elif self.path == "/metricas":
            self._set_headers(200)
            self.wfile.write(json.dumps(MODELO["metrics"]).encode("utf-8"))
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
