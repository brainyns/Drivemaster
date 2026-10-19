FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# Vite incrusta estas variables en el JS al compilar: se pasan como build args.
# Vacías = mismo comportamiento de antes (http://localhost:8080).
ARG VITE_API_URL=
ARG VITE_WOMPI_REDIRECT_URL=
ARG VITE_GOOGLE_CLIENT_ID=
# Mapas (opcionales): vacías = servicios públicos gratuitos (CARTO, Photon, Nominatim, OSRM)
ARG VITE_MAPA_TILES_CLARO=
ARG VITE_MAPA_TILES_OSCURO=
ARG VITE_PHOTON_URL=
ARG VITE_NOMINATIM_URL=
ARG VITE_NOMINATIM_EMAIL=
ARG VITE_OSRM_URL=
ENV VITE_API_URL=$VITE_API_URL \
    VITE_WOMPI_REDIRECT_URL=$VITE_WOMPI_REDIRECT_URL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID \
    VITE_MAPA_TILES_CLARO=$VITE_MAPA_TILES_CLARO \
    VITE_MAPA_TILES_OSCURO=$VITE_MAPA_TILES_OSCURO \
    VITE_PHOTON_URL=$VITE_PHOTON_URL \
    VITE_NOMINATIM_URL=$VITE_NOMINATIM_URL \
    VITE_NOMINATIM_EMAIL=$VITE_NOMINATIM_EMAIL \
    VITE_OSRM_URL=$VITE_OSRM_URL
RUN CI=false npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
