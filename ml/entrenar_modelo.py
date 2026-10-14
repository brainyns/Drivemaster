"""
Entrenamiento y Evaluación del Modelo Predictivo de Aprobación de Cotizaciones
Proyecto de Aula - DriveMaster
Autores: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello.
"""

import csv
import json
import math
import os
import random

random.seed(42)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATASET_PATH = os.path.join(BASE_DIR, "dataset_cotizaciones_drivemaster.csv")

if not os.path.exists(DATASET_PATH):
    raise FileNotFoundError("Dataset no encontrado.")

# 1. Cargar datos
with open(DATASET_PATH, "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    rows = list(reader)

random.shuffle(rows)

# Variables numéricas y categóricas
NUMERIC_FEATURES = [
    "anio_vehiculo",
    "distancia_km",
    "cantidad_repuestos",
    "num_tiendas",
    "monto_repuestos",
    "monto_mano_obra",
    "total_con_iva",
    "proporcion_repuestos",
    "tiempo_respuesta_horas",
    "calificacion_taller"
]

MARCAS_CONOCIDAS = ["Chevrolet", "Renault", "Mazda", "Toyota", "Kia", "Nissan", "Hyundai", "Ford"]
SERVICIOS_CONOCIDOS = [
    "MANTENIMIENTO_PREVENTIVO", "FRENOS", "SUSPENSION",
    "MOTOR_AFINAMIENTO", "SISTEMA_ELECTRICO", "DIAGNOSTICO_GENERAL", "EMBRAGUE_TRANSMISION"
]

# Construcción de vector de características (X) y objetivo (y)
def extraer_features(r):
    x = []
    for num_col in NUMERIC_FEATURES:
        x.append(float(r[num_col]))
    # One-hot encoding marcas
    for m in MARCAS_CONOCIDAS:
        x.append(1.0 if r["marca_vehiculo"] == m else 0.0)
    # One-hot encoding tipos de servicio
    for s in SERVICIOS_CONOCIDOS:
        x.append(1.0 if r["tipo_servicio"] == s else 0.0)
    return x

X_all = [extraer_features(r) for r in rows]
y_all = [int(r["aprobacion_cotizacion"]) for r in rows]

# Nombres de todas las características
ALL_FEATURE_NAMES = list(NUMERIC_FEATURES) + [f"marca_{m}" for m in MARCAS_CONOCIDAS] + [f"servicio_{s}" for s in SERVICIOS_CONOCIDOS]

# Split Train / Test (80% / 20%)
split_idx = int(len(X_all) * 0.8)
X_train_raw, X_test_raw = X_all[:split_idx], X_all[split_idx:]
y_train, y_test = y_all[:split_idx], y_all[split_idx:]

# Estandarización de variables numéricas (Z-score)
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

# 2. Entrenar Regresión Logística con Descenso de Gradiente y Regularización L2
def sigmoide(z):
    z = max(-50.0, min(50.0, z))
    return 1.0 / (1.0 + math.exp(-z))

num_weights = len(ALL_FEATURE_NAMES)
weights = [0.0] * num_weights
bias = 0.0
learning_rate = 0.08
epochs = 350
lambda_l2 = 0.001
N_train = len(X_train)

for epoch in range(epochs):
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
        weights[j] -= learning_rate * ((grad_w[j] / N_train) + lambda_l2 * weights[j])
    bias -= learning_rate * (grad_b / N_train)

# 3. Evaluación en conjunto de prueba
y_pred_prob = []
y_pred = []

for i in range(len(X_test)):
    z = bias + sum(weights[j] * X_test[i][j] for j in range(num_weights))
    prob = sigmoide(z)
    y_pred_prob.append(prob)
    y_pred.append(1 if prob >= 0.50 else 0)

# Métricas de Clasificación
tp = sum(1 for i in range(len(y_test)) if y_test[i] == 1 and y_pred[i] == 1)
tn = sum(1 for i in range(len(y_test)) if y_test[i] == 0 and y_pred[i] == 0)
fp = sum(1 for i in range(len(y_test)) if y_test[i] == 0 and y_pred[i] == 1)
fn = sum(1 for i in range(len(y_test)) if y_test[i] == 1 and y_pred[i] == 0)

total_test = len(y_test)
accuracy = (tp + tn) / total_test
precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0

# Cálculo de ROC-AUC aproximado (Wilcoxon-Mann-Whitney)
pos_scores = [y_pred_prob[i] for i in range(total_test) if y_test[i] == 1]
neg_scores = [y_pred_prob[i] for i in range(total_test) if y_test[i] == 0]
auc_sum = 0
for p in pos_scores:
    for n in neg_scores:
        if p > n:
            auc_sum += 1
        elif p == n:
            auc_sum += 0.5
roc_auc = auc_sum / (len(pos_scores) * len(neg_scores)) if pos_scores and neg_scores else 0.5

print("=======================================================")
print("RESULTADOS DEL MODELO PREDICTIVO (REGRESIÓN LOGÍSTICA)")
print("=======================================================")
print(f"Exactitud (Accuracy):  {accuracy * 100:.2f}%")
print(f"Precisión (Precision): {precision * 100:.2f}%")
print(f"Sensibilidad (Recall): {recall * 100:.2f}%")
print(f"Puntuación F1:         {f1 * 100:.2f}%")
print(f"ROC-AUC:               {roc_auc:.4f}")
print("Matriz de Confusión:")
print(f"  Verdaderos Negativos (Rechazos acertados):  {tn}")
print(f"  Falsos Positivos (Rechazos como Aprobados): {fp}")
print(f"  Falsos Negativos (Aprobados como Rechazos): {fn}")
print(f"  Verdaderos Positivos (Aprobados acertados): {tp}")

# 4. Guardar archivo de pesos para inferencia portable (Python y Java)
modelo_dict = {
    "numeric_features": NUMERIC_FEATURES,
    "means": [round(m, 4) for m in means],
    "stds": [round(s, 4) for s in stds],
    "marcas": MARCAS_CONOCIDAS,
    "tipos_servicio": SERVICIOS_CONOCIDOS,
    "feature_names": ALL_FEATURE_NAMES,
    "weights": [round(w, 5) for w in weights],
    "bias": round(bias, 5),
    "metrics": {
        "accuracy": round(accuracy, 4),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1_score": round(f1, 4),
        "roc_auc": round(roc_auc, 4),
        "confusion_matrix": [[tn, fp], [fn, tp]],
        "total_entrenamiento": len(X_train),
        "total_prueba": len(X_test)
    }
}

JSON_PATH = os.path.join(BASE_DIR, "modelo_pesos.json")
with open(JSON_PATH, "w", encoding="utf-8") as f:
    json.dump(modelo_dict, f, indent=2, ensure_ascii=False)
print(f"\nModelo guardado en: {JSON_PATH}")

# 5. Generar reporte académico
REPORTE_PATH = os.path.join(BASE_DIR, "reporte_metricas_aula.txt")
with open(REPORTE_PATH, "w", encoding="utf-8") as f:
    f.write("="*75 + "\n")
    f.write("PROYECTO DE AULA: MODELO PREDICTIVO PARA ESTIMAR LA PROBABILIDAD DE APROBACIÓN\n")
    f.write("DE UNA COTIZACIÓN DE SERVICIO EN LA PLATAFORMA DRIVEMASTER\n")
    f.write("Integrantes: Brainy Blanco, Sebastian Ortega, Juan Cantillo, Elias Puello\n")
    f.write("="*75 + "\n\n")
    f.write("1. RESUMEN DE DATOS:\n")
    f.write(f"   - Total de registros: {len(rows)}\n")
    f.write(f"   - Tamaño conjunto de entrenamiento (80%): {len(X_train)}\n")
    f.write(f"   - Tamaño conjunto de prueba (20%):        {len(X_test)}\n\n")
    f.write("2. MÉTRICAS DE CLASIFICACIÓN (Evaluadas en conjunto de prueba independiente):\n")
    f.write(f"   - Exactitud (Accuracy):  {accuracy * 100:.2f}%\n")
    f.write(f"   - Precisión (Precision): {precision * 100:.2f}%\n")
    f.write(f"   - Sensibilidad (Recall): {recall * 100:.2f}%\n")
    f.write(f"   - Puntuación F1:         {f1 * 100:.2f}%\n")
    f.write(f"   - Área Bajo la Curva ROC (ROC-AUC): {roc_auc:.4f}\n\n")
    f.write("3. MATRIZ DE CONFUSIÓN:\n")
    f.write(f"   - Verdaderos Negativos (TN): {tn}\n")
    f.write(f"   - Falsos Positivos    (FP): {fp}\n")
    f.write(f"   - Falsos Negativos    (FN): {fn}\n")
    f.write(f"   - Verdaderos Positivos (TP): {tp}\n\n")
    f.write("4. COEFICIENTES DESTACADOS DEL MODELO (Importancia de Variables):\n")
    # Ordenar por magnitud de impacto
    impactos = sorted(zip(ALL_FEATURE_NAMES, weights), key=lambda x: abs(x[1]), reverse=True)
    for feat, w in impactos[:8]:
        direccion = "(Favorece aprobación)" if w > 0 else "(Favorece rechazo)"
        f.write(f"   - {feat:25s}: {w:+.4f}  {direccion}\n")

print(f"Reporte del proyecto de aula guardado en: {REPORTE_PATH}")
