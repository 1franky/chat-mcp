# ADR-0007: Credenciales por usuario y salida a proveedores

- Estado: Accepted
- Fecha: 2026-07-15

## Contexto

Sprint 2 incorpora conexiones LLM propiedad de usuarios. Sus claves no pueden quedar en texto
claro, regresar al navegador ni convertir al backend en un proxy SSRF. Los proveedores publican
catálogos y capacidades heterogéneos, y BytePlus no garantiza un catálogo de inferencia equivalente
a OpenAI Models para todos los productos.

## Decisión

Persistir conexiones y modelos en `chat.provider_connection` y `chat.provider_model`, consultando
siempre por `owner_id`. Cifrar claves con AES-256-GCM, nonce aleatorio por escritura, AAD y versión
de clave. La clave maestra vive fuera de PostgreSQL y el API sólo expone una pista enmascarada.

Mantener un adaptador por familia detrás de `LlmProviderPort`. Representar capacidades como
`SUPPORTED`, `UNSUPPORTED` o `UNKNOWN`, y conservar el origen de modelo `DISCOVERED`, `MANUAL` o
`CONFIGURED`. No inferir capacidades por nombre ni inventar catálogos ausentes.

Para bases configurables, exigir allowlist de host del operador, HTTPS salvo HTTP interno
explícito, validación DNS, bloqueo de link-local, límites de tiempo/tamaño y redirects desactivados.
Las pruebas usan el fake y servidores HTTP locales; ninguna llama a servicios pagados.

## Consecuencias

La UI nunca puede recuperar una clave guardada y reemplazarla requiere enviar una nueva. La
rotación futura puede localizar registros por versión, pero Sprint 2 sólo carga una clave activa.
BytePlus y compatibles sin catálogo requieren un ID configurado; su validación explícita puede
consumir una solicitud mínima. Streaming, tools y selección dentro del chat siguen fuera de alcance
hasta Sprint 3.
