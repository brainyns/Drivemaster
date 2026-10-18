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
ENV VITE_API_URL=$VITE_API_URL \
    VITE_WOMPI_REDIRECT_URL=$VITE_WOMPI_REDIRECT_URL \
    VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
RUN CI=false npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
