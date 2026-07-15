FROM node:24.15.0-alpine@sha256:d1b3b4da11eefd5941e7f0b9cf17783fc99d9c6fc34884a665f40a06dbdfc94f AS build

WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28.0-alpine@sha256:c97ff0bf7cbae369953c6da1232ec14ad9f971d66360c5698db0856a4cd657a0

COPY deployment/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html

USER 101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
