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
ENV VITE_API_URL=$VITE_API_URL \
    VITE_WOMPI_REDIRECT_URL=$VITE_WOMPI_REDIRECT_URL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
RUN CI=false npm run build

FROM nginx:alpine
COPY homelab/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
