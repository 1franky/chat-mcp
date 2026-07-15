# Proveedores LLM

## Sprint 0

Sólo existe `FakeLlmProviderAdapter`. Es determinista, anuncia el modelo `fake-chat-v1`, no necesita credenciales y devuelve chunks controlados para pruebas. Implementa puertos propios; el dominio no conoce SDKs.

El BOM de Spring AI 2.0.0 fija compatibilidad futura, pero no hay starter de OpenAI, Anthropic, BytePlus, Ollama ni otro proveedor en runtime. Por tanto, Sprint 0 no puede enviar prompts a servicios reales.

## Contrato

- `LlmProviderPort`: generación por chunks con mensajes del dominio.
- `ModelCatalogPort`: descriptor de proveedor y catálogo de modelos.
- `ProviderCapabilities`: capacidades declaradas sin tipos de SDK.

La selección, validación, cifrado de credenciales y adaptadores reales pertenecen a Sprint 2. Cualquier proveedor futuro debe quedar detrás de estos puertos, tener timeouts y pruebas con dobles; las pruebas normales nunca deben consumir APIs pagadas.
