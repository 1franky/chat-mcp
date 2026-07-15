# ADR-0001: Monolito modular hexagonal

- Estado: Accepted
- Fecha: 2026-07-15

## Contexto

El producto necesita múltiples integraciones, pero Sprint 0 no justifica coordinación distribuida. El dominio no puede depender de SDKs externos.

## Decisión

Usar un único backend desplegable con paquetes `domain`, `application`, `application.port`, `adapters`, `infrastructure`, `web` y `configuration`. Las dependencias apuntan hacia dominio/application y ArchUnit protege las reglas principales.

## Consecuencias

El despliegue y las transacciones permanecen simples. Los adaptadores pueden evolucionar de fake a reales sin alterar el dominio. La disciplina modular deberá mantenerse; separar procesos será una decisión futura basada en evidencia, no una condición inicial.
