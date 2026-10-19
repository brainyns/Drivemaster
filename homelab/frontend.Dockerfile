# Imagen del frontend para el HomeLab: el build de React de frontend/ + homelab/nginx.conf.
# Se construye con la raíz del repositorio como contexto (ver docker-compose.yml).
# A diferencia de frontend/Dockerfile, la configuración de nginx va dentro de la imagen: Portainer CE
# no puede montar ./nginx.conf desde un stack de Git.

FROM node:20-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ .
# Vite incrusta estas variables en el JS al compilar.
ARG VITE_API_URL=
ARG VITE_WOMPI_REDIRECT_URL=
ARG VITE_GOOGLE_CLIENT_ID=
# Mapas (opcionales): vacías = servicios públicos gratuitos (teselas de OpenStreetMap, Photon, Nominatim, OSRM)
ARG VITE_MAP_TILES_URL=
ARG VITE_MAP_TILES_ATTRIBUTION=
ARG VITE_PHOTON_URL=
ARG VITE_NOMINATIM_URL=
ARG VITE_NOMINATIM_EMAIL=
ARG VITE_OSRM_URL=
ENV VITE_API_URL=$VITE_API_URL \
    VITE_WOMPI_REDIRECT_URL=$VITE_WOMPI_REDIRECT_URL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID \
    VITE_MAP_TILES_URL=$VITE_MAP_TILES_URL \
    VITE_MAP_TILES_ATTRIBUTION=$VITE_MAP_TILES_ATTRIBUTION \
    VITE_PHOTON_URL=$VITE_PHOTON_URL \
    VITE_NOMINATIM_URL=$VITE_NOMINATIM_URL \
    VITE_NOMINATIM_EMAIL=$VITE_NOMINATIM_EMAIL \
    VITE_OSRM_URL=$VITE_OSRM_URL
RUN CI=false npm run build

FROM nginx:alpine
COPY homelab/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
