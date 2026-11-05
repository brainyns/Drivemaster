"""
Entrenamiento y Evaluación del Modelo Predictivo de Aprobación de Cotizaciones
Proyecto de Aula - DriveMaster (Machine Learning con Optimización Adam y Feature Engineering)
Autores: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello.
"""

import csv
import json
import math
import os
import random
import shutil

random.seed(42)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATASET_PATH = os.path.join(BASE_DIR, "dataset_cotizaciones_drivemaster.csv")
JAVA_RESOURCES_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "drivemaster", "src", "main", "resources"))

if not os.path.exists(DATASET_PATH):
    raise FileNotFoundError("Dataset no encontrado.")

# 1. Cargar datos
with open(DATASET_PATH, "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    rows = list(reader)

random.shuffle(rows)

MARCAS_CONOCIDAS = ["Chevrolet", "Renault", "Mazda", "Toyota", "Kia", "Nissan", "Hyundai", "Ford"]
SERVICIOS_CONOCIDOS = [
    "MANTENIMIENTO_PREVENTIVO", "FRENOS", "SUSPENSION",
    "MOTOR_AFINAMIENTO", "SISTEMA_ELECTRICO", "DIAGNOSTICO_GENERAL", "EMBRAGUE_TRANSMISION"
]

NUMERIC_FEATURES = [
    "antiguedad_vehiculo",
    "distancia_km",
    "log_distancia",
    "cantidad_repuestos",
    "num_tiendas",
    "monto_repuestos",
    "monto_mano_obra",
    "total_con_iva",
    "proporcion_repuestos",
    "tiempo_respuesta_horas",
    "log_tiempo_respuesta",
    "calificacion_taller",
    "reputacion_vs_distancia",
    "antiguedad_x_precio"
]

def extraer_features(r):
    anio = float(r.get("anio_vehiculo", 2018))
    antiguedad = max(0.0, 2026.0 - anio)
    dist = float(r.get("distancia_km", 5.0))
    log_dist = math.log(max(dist, 0.1) + 1.0)
    cant_rep = float(r.get("cantidad_repuestos", 2))
    num_t = float(r.get("num_tiendas", 1))
    m_rep = float(r.get("monto_repuestos", 0.0))
    m_mo = float(r.get("monto_mano_obra", 0.0))
    tot = float(r.get("total_con_iva", 0.0))
    if tot <= 0:
        tot = (m_rep + m_mo) * 1.19
    prop_rep = float(r.get("proporcion_repuestos", round(m_rep / tot, 3) if tot > 0 else 0.0))
    tiempo = float(r.get("tiempo_respuesta_horas", 2.0))
    log_tiempo = math.log(max(tiempo, 0.1) + 1.0)
    calif = float(r.get("calificacion_taller", 4.6))
    rep_dist = calif / (log_dist + 0.5)
    antig_precio = (antiguedad / 10.0) * (tot / 1000000.0)

    vec = [
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

    marca_r = str(r.get("marca_vehiculo", "Chevrolet")).strip().lower()
    for m in MARCAS_CONOCIDAS:
        vec.append(1.0 if marca_r == m.lower() else 0.0)

    serv_r = str(r.get("tipo_servicio", "MANTENIMIENTO_PREVENTIVO")).strip().upper()
    for s in SERVICIOS_CONOCIDOS:
        vec.append(1.0 if serv_r == s.upper() else 0.0)

    return vec

X_all = [extraer_features(r) for r in rows]
y_all = [int(r["aprobacion_cotizacion"]) for r in rows]

ALL_FEATURE_NAMES = list(NUMERIC_FEATURES) + [f"marca_{m}" for m in MARCAS_CONOCIDAS] + [f"servicio_{s}" for s in SERVICIOS_CONOCIDOS]

# Split Train / Test (80% / 20%)
split_idx = int(len(X_all) * 0.8)
X_train_raw, X_test_raw = X_all[:split_idx], X_all[split_idx:]
y_train, y_test = y_all[:split_idx], y_all[split_idx:]

num_count = len(NUMERIC_FEATURES)
means = [0.0] * num_count
stds = [0.0] * num_count

for j in range(num_count):
    vals = [X_train_raw[i][j] for i in range(len(X_train_raw))]
    mean_val = sum(vals) / len(vals)
    variance = sum((v - mean_val) ** 2 for v in vals) / len(vals)
    std_val = math.sqrt(variance) if variance > 1e-9 else 1.0
    means[j] = mean_val
    stds[j] = std_val

def normalizar_vector(x_vec):
    norm = list(x_vec)
    for j in range(num_count):
        norm[j] = (norm[j] - means[j]) / stds[j]
    return norm

X_train = [normalizar_vector(x) for x in X_train_raw]
X_test = [normalizar_vector(x) for x in X_test_raw]

def sigmoide(z):
    z = max(-50.0, min(50.0, z))
    return 1.0 / (1.0 + math.exp(-z))

# 2. Entrenamiento con Optimizador Adam y Regularización L2
num_weights = len(ALL_FEATURE_NAMES)
weights = [0.0] * num_weights
bias = 0.0
lr = 0.06
epochs = 500
lambda_l2 = 0.0001
N_train = len(X_train)

m_w = [0.0] * num_weights
v_w = [0.0] * num_weights
m_b = 0.0
v_b = 0.0
beta1, beta2, eps = 0.9, 0.999, 1e-8

for epoch in range(1, epochs + 1):
    grad_w = [0.0] * num_weights
    grad_b = 0.0

    for i in range(N_train):
        z = bias + sum(weights[j] * X_train[i][j] for j in range(num_weights))
        p = sigmoide(z)
        error = p - y_train[i]
        for j in range(num_weights):
            grad_w[j] += error * X_train[i][j]
        grad_b += error

    for j in range(num_weights):
        g = (grad_w[j] / N_train) + lambda_l2 * weights[j]
        m_w[j] = beta1 * m_w[j] + (1 - beta1) * g
        v_w[j] = beta2 * v_w[j] + (1 - beta2) * (g ** 2)
        m_hat = m_w[j] / (1 - beta1 ** epoch)
        v_hat = v_w[j] / (1 - beta2 ** epoch)
        weights[j] -= lr * m_hat / (math.sqrt(v_hat) + eps)

    gb = grad_b / N_train
    m_b = beta1 * m_b + (1 - beta1) * gb
    v_b = beta2 * v_b + (1 - beta2) * (gb ** 2)
    mb_hat = m_b / (1 - beta1 ** epoch)
    vb_hat = v_b / (1 - beta2 ** epoch)
    bias -= lr * mb_hat / (math.sqrt(vb_hat) + eps)

# 3. Calibración de Umbral Óptimo en Conjunto de Entrenamiento (Youden / F1)
train_probs = [sigmoide(bias + sum(weights[j] * X_train[i][j] for j in range(num_weights))) for i in range(N_train)]

best_th = 0.50
best_f1_train = 0.0
for th_int in range(35, 66, 1):
    th = th_int / 100.0
    th_preds = [1 if p >= th else 0 for p in train_probs]
    tp_t = sum(1 for i in range(N_train) if y_train[i] == 1 and th_preds[i] == 1)
    fp_t = sum(1 for i in range(N_train) if y_train[i] == 0 and th_preds[i] == 1)
    fn_t = sum(1 for i in range(N_train) if y_train[i] == 1 and th_preds[i] == 0)
    p_t = tp_t / (tp_t + fp_t) if (tp_t + fp_t) > 0 else 0.0
    r_t = tp_t / (tp_t + fn_t) if (tp_t + fn_t) > 0 else 0.0
    f1_t = 2 * (p_t * r_t) / (p_t + r_t) if (p_t + r_t) > 0 else 0.0
    if f1_t > best_f1_train:
        best_f1_train = f1_t
        best_th = th

# 4. Evaluación en Conjunto de Prueba
test_probs = []
test_preds = []

for i in range(len(X_test)):
    z = bias + sum(weights[j] * X_test[i][j] for j in range(num_weights))
    prob = sigmoide(z)
    test_probs.append(prob)
    test_preds.append(1 if prob >= best_th else 0)

tp = sum(1 for i in range(len(y_test)) if y_test[i] == 1 and test_preds[i] == 1)
tn = sum(1 for i in range(len(y_test)) if y_test[i] == 0 and test_preds[i] == 0)
fp = sum(1 for i in range(len(y_test)) if y_test[i] == 0 and test_preds[i] == 1)
fn = sum(1 for i in range(len(y_test)) if y_test[i] == 1 and test_preds[i] == 0)

total_test = len(y_test)
accuracy = (tp + tn) / total_test
precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0

# ROC-AUC (Wilcoxon-Mann-Whitney)
pos_scores = [test_probs[i] for i in range(total_test) if y_test[i] == 1]
neg_scores = [test_probs[i] for i in range(total_test) if y_test[i] == 0]
auc_sum = sum(1.0 if p > n else (0.5 if p == n else 0.0) for p in pos_scores for n in neg_scores)
roc_auc = auc_sum / (len(pos_scores) * len(neg_scores)) if pos_scores and neg_scores else 0.5

print("=========================================================================")
print("RESULTADOS DEL MODELO PREDICTIVO PULIDO (DRIVEMASTER ML - REG. LOGÍSTICA)")
print("=========================================================================")
print(f"Exactitud (Accuracy):  {accuracy * 100:.2f}%")
print(f"Precisión (Precision): {precision * 100:.2f}%")
print(f"Sensibilidad (Recall): {recall * 100:.2f}%")
print(f"Puntuación F1:         {f1 * 100:.2f}%")
print(f"Área ROC (ROC-AUC):    {roc_auc:.4f}")
print(f"Umbral Óptimo:         {best_th:.2f}")
print("Matriz de Confusión:")
print(f"  Verdaderos Negativos (TN): {tn}")
print(f"  Falsos Positivos    (FP): {fp}")
print(f"  Falsos Negativos    (FN): {fn}")
print(f"  Verdaderos Positivos (TP): {tp}")

# 5. Exportar modelo_pesos.json para inferencia portable en Python y Java
modelo_dict = {
    "version": "2.0-adam-xai",
    "numeric_features": NUMERIC_FEATURES,
    "means": [round(m, 5) for m in means],
    "stds": [round(s, 5) for s in stds],
    "marcas": MARCAS_CONOCIDAS,
    "tipos_servicio": SERVICIOS_CONOCIDOS,
    "feature_names": ALL_FEATURE_NAMES,
    "weights": [round(w, 5) for w in weights],
    "bias": round(bias, 5),
    "optimal_threshold": round(best_th, 4),
    "metrics": {
        "accuracy": round(accuracy, 4),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1_score": round(f1, 4),
        "roc_auc": round(roc_auc, 4),
        "optimal_threshold": round(best_th, 4),
        "confusion_matrix": [[tn, fp], [fn, tp]],
        "total_entrenamiento": len(X_train),
        "total_prueba": len(X_test),
        "total_registros": len(rows)
    }
}

JSON_PATH = os.path.join(BASE_DIR, "modelo_pesos.json")
with open(JSON_PATH, "w", encoding="utf-8") as f:
    json.dump(modelo_dict, f, indent=2, ensure_ascii=False)
print(f"\nModelo guardado en: {JSON_PATH}")

# Sincronizar automáticamente con resources de Java Spring Boot
if os.path.exists(JAVA_RESOURCES_DIR):
    java_dest = os.path.join(JAVA_RESOURCES_DIR, "modelo_pesos.json")
    shutil.copyfile(JSON_PATH, java_dest)
    print(f"Sincronizado con Spring Boot: {java_dest}")

# 6. Generar reporte académico
REPORTE_PATH = os.path.join(BASE_DIR, "reporte_metricas_aula.txt")
with open(REPORTE_PATH, "w", encoding="utf-8") as f:
    f.write("=" * 75 + "\n")
    f.write("PROYECTO DE AULA: MODELO PREDICTIVO PARA ESTIMAR LA PROBABILIDAD DE APROBACIÓN\n")
    f.write("DE UNA COTIZACIÓN DE SERVICIO EN LA PLATAFORMA DRIVEMASTER (VERSIÓN PULIDA)\n")
    f.write("Integrantes: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello\n")
    f.write("=" * 75 + "\n\n")
    f.write("1. RESUMEN DE DATOS:\n")
    f.write(f"   - Total de registros: {len(rows)}\n")
    f.write(f"   - Tamaño conjunto de entrenamiento (80%): {len(X_train)}\n")
    f.write(f"   - Tamaño conjunto de prueba (20%):        {len(X_test)}\n")
    f.write(f"   - Tasa de aprobación en dataset:          {sum(y_all) / len(y_all) * 100:.2f}%\n\n")
    f.write("2. MÉTRICAS DE CLASIFICACIÓN (Evaluadas en conjunto de prueba independiente):\n")
    f.write(f"   - Exactitud (Accuracy):  {accuracy * 100:.2f}%\n")
    f.write(f"   - Precisión (Precision): {precision * 100:.2f}%\n")
    f.write(f"   - Sensibilidad (Recall): {recall * 100:.2f}%\n")
    f.write(f"   - Puntuación F1:         {f1 * 100:.2f}%\n")
    f.write(f"   - Área Bajo la Curva ROC (ROC-AUC): {roc_auc:.4f}\n")
    f.write(f"   - Umbral de decisión calibrado:     {best_th:.2f}\n\n")
    f.write("3. MATRIZ DE CONFUSIÓN:\n")
    f.write(f"   - Verdaderos Negativos (TN): {tn}\n")
    f.write(f"   - Falsos Positivos    (FP): {fp}\n")
    f.write(f"   - Falsos Negativos    (FN): {fn}\n")
    f.write(f"   - Verdaderos Positivos (TP): {tp}\n\n")
    f.write("4. COEFICIENTES DESTACADOS DEL MODELO (Importancia e Impacto de Variables):\n")
    impactos = sorted(zip(ALL_FEATURE_NAMES, weights), key=lambda x: abs(x[1]), reverse=True)
    for feat, w in impactos[:12]:
        direccion = "(Favorece aprobación)" if w > 0 else "(Favorece rechazo)"
        f.write(f"   - {feat:25s}: {w:+.4f}  {direccion}\n")

print(f"Reporte académico guardado en: {REPORTE_PATH}")
