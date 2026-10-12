#!/usr/bin/env bash
# Respaldo de las bases de datos de DriveMaster en la nube (MongoDB Atlas y Supabase/PostgreSQL)
# y del volumen de n8n (workflows del chat IA, ejecuciones y credenciales).
#
# Uso:      ./backup.sh [carpeta_destino]          (por defecto ./backups, junto al script)
# Con cron: 0 3 * * * /opt/drivemaster/backup.sh /mnt/nas/backups/drivemaster
#
# No necesita .env: lee SPRING_DATA_MONGODB_URI, DB_URL, DB_USERNAME y DB_PASSWORD del contenedor del
# backend del stack (Portainer). Ejecuta mongodump y pg_dump en contenedores temporales (--rm), así que
# no hay que instalar nada en la VM.
# Si tu stack en Portainer no se llama "drivemaster":  STACK=otro_nombre ./backup.sh
#
# Importante: el plan gratuito de Atlas (M0) NO tiene backups automáticos. Este script es tu respaldo.
# Guarda un .archive.gz de Mongo y un .sql.gz de Postgres con fecha, y borra los de más de 14 días.
# Recuerda tu plan: "Almacenamiento ≠ Backup". Lleva esta carpeta a OTRO equipo (NAS, G52, disco externo).

set -euo pipefail

DIR_SCRIPT="$(cd "$(dirname "$0")" && pwd)"
DESTINO="${1:-$DIR_SCRIPT/backups}"
STACK="${STACK:-drivemaster}"
DIAS_RETENCION=14
FECHA="$(date +%Y-%m-%d_%H%M)"
# pg_dump debe ser de la misma versión mayor que el servidor de Supabase, o más nueva.
IMAGEN_PG="postgres:17-alpine"
IMAGEN_MONGO="mongo:7.0"

BACKEND="$(docker ps -q \
  --filter "label=com.docker.compose.project=$STACK" \
  --filter "label=com.docker.compose.service=backend")"
if [ -z "$BACKEND" ]; then
  echo "No encontré el backend del stack '$STACK' en ejecución." >&2
  exit 1
fi

variable() { docker exec "$BACKEND" printenv "$1"; }

MONGO_URI="$(variable SPRING_DATA_MONGODB_URI)"
DB_URL="$(variable DB_URL)"
PGUSER="$(variable DB_USERNAME)"
PGPASSWORD="$(variable DB_PASSWORD)"
# JDBC (jdbc:postgresql://host:puerto/base?opciones) → URI de libpq. Se quitan las opciones propias de
# JDBC (pg_dump no las entiende) y se exige SSL, que Supabase requiere.
PG_URI="${DB_URL#jdbc:}"
PG_URI="${PG_URI%%\?*}?sslmode=require"
export MONGO_URI PG_URI PGUSER PGPASSWORD

mkdir -p "$DESTINO"

# Las credenciales viajan como variables de entorno (-e NOMBRE), no en la línea de comandos,
# para que no aparezcan en la lista de procesos.
echo "[$(date)] Respaldando MongoDB Atlas..."
docker run --rm -e MONGO_URI "$IMAGEN_MONGO" \
  sh -c 'mongodump --uri "$MONGO_URI" --archive --gzip --quiet' \
  > "$DESTINO/mongo_${FECHA}.archive.gz"

echo "[$(date)] Respaldando Supabase (PostgreSQL)..."
docker run --rm -e PG_URI -e PGUSER -e PGPASSWORD "$IMAGEN_PG" \
  sh -c 'pg_dump --no-owner --no-privileges --schema=public "$PG_URI"' \
  | gzip > "$DESTINO/postgres_${FECHA}.sql.gz"

echo "[$(date)] Respaldando n8n (workflows, ejecuciones y credenciales)..."
if docker volume ls -q | grep -qx "${STACK}_n8n_data"; then
  docker run --rm -v "${STACK}_n8n_data:/data:ro" -v "$DESTINO:/backup" alpine:3.20 \
    sh -c "tar czf /backup/n8n_${FECHA}.tar.gz -C /data ."
else
  echo "Volumen ${STACK}_n8n_data no encontrado; omito el respaldo de n8n."
fi

find "$DESTINO" -name 'mongo_*.archive.gz' -mtime +"$DIAS_RETENCION" -delete
find "$DESTINO" -name 'postgres_*.sql.gz'  -mtime +"$DIAS_RETENCION" -delete
find "$DESTINO" -name 'n8n_*.tar.gz'       -mtime +"$DIAS_RETENCION" -delete

echo "[$(date)] Listo en $DESTINO"
ls -lh "$DESTINO" | tail -n 4
