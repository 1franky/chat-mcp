# ADR-0002: Baseline de versiones y ARM64

- Estado: Accepted
- Fecha: 2026-07-15

## Contexto

La especificación exige versiones estables, Java 21 y compatibilidad Linux ARM64. Etiquetas flotantes impedirían builds reproducibles.

## Decisión

Compilar a Java 21 con Spring Boot 4.1.0 y BOM Spring AI 2.0.0; usar Angular 22.0.6, PostgreSQL 18.4 y pgvector 0.8.2. Fijar imágenes por versión y digest y verificar que sus manifiestos incluyan `linux/arm64`.

## Consecuencias

Los builds son reproducibles y ARM64 es un objetivo explícito. Los parches requieren una actualización deliberada con verificación de fuentes, pruebas, digests y este registro de baseline.
