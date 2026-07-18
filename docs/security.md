# Seguridad

## Identidad y sesiones

- Spring Security autentica con correo normalizado y contrasenas Argon2id.
- Spring Session JDBC persiste el estado server-side en PostgreSQL; el navegador solo recibe un
  identificador opaco.
- La cookie `AI_DATA_CHAT_SESSION` es `HttpOnly`, `SameSite=Lax` y configurable como `Secure`.
- El identificador de sesion cambia al autenticar y logout invalida realmente la sesion.
- El principal persistido en sesion borra el hash de contrasena antes de serializarse.
- Angular no guarda JWT, cookies, contrasenas ni secretos en `localStorage`/`sessionStorage`.

## CSRF y autorizacion

El repositorio CSRF usa cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN` con el handler recomendado
para SPA de Spring Security. La cookie CSRF debe configurarse como `Secure` en produccion. Los
tokens CSRF se eliminan al autenticar y cerrar sesion; la siguiente consulta de bootstrap emite uno
nuevo. Los healthchecks, estado y bootstrap son publicos; registro y login son publicos pero siguen exigiendo
CSRF. `/api/admin/**` requiere `ROLE_ADMIN`; el resto no permitido se deniega.

El rol nunca se acepta en registro o alta administrativa. El servidor asigna el primer `ADMIN` y
despues `USER`. El conteo y cambio del ultimo administrador se serializan para impedir carreras.
Ser administrador solo habilita gestion de usuarios y no otorga acceso a recursos privados de
otras cuentas.

## Abuso y errores

- Login permite por defecto 5 solicitudes por minuto y registro 10 por hora, por ruta y direccion
  remota. Al exceder devuelve `429` y `Retry-After`.
- Los limites se configuran con `LOGIN_RATE_LIMIT_*` y `REGISTRATION_RATE_LIMIT_*`.
- El limite actual reside en memoria por instancia. Antes de escalar horizontalmente debe migrarse
  a un almacen compartido y definirse una frontera de proxies confiables.
- Nginx sobrescribe `X-Forwarded-For` con la direccion del socket cliente; no concatena valores
  aportados por Internet. El backend no debe exponerse como un segundo punto de ingreso.
- Los fallos de login usan un mensaje generico para no enumerar usuarios.
- Problem Details no expone stack traces, hashes ni mensajes internos.
- La auditoria registra metadata acotada y nunca contrasenas, cookies, tokens o cuerpos completos.

## Despliegue

- PostgreSQL no publica puerto al host.
- Backend, frontend y fake MCP se ejecutan como usuarios no-root, con filesystem de solo lectura,
  `cap_drop: ALL`, `no-new-privileges` y temporales en `tmpfs`.
- Nginx aplica CSP, HSTS, `nosniff`, `DENY`, Referrer-Policy y Permissions-Policy.
- En produccion se exige HTTPS y deben activarse `SESSION_COOKIE_SECURE=true` y
  `CSRF_COOKIE_SECURE=true`.
- `.env.example` solo contiene marcadores; `.env` permanece ignorado.

## Limites conscientes

- El rate limiting local no coordina replicas. La topologia asume que toda API de navegador entra
  por Nginx; otro servicio conectado directamente a la red interna queda fuera de esa frontera.
- No existe recuperacion de contrasena, MFA ni politica de expiracion de credenciales en Sprint 1.
- Las bajas son logicas y no permiten reutilizar correo; una politica de borrado completo requiere
  una decision futura.
- La rotacion de claves de proveedor entre varias versiones sigue pendiente; Sprint 2 carga una
  unica version activa.
- El cliente MCP real (Sprint 4) sigue sin autenticacion porque Data Platform MCP no la tiene todavia;
  solo backend pertenece a `ai-platform` y `McpAuthProvider` documenta el punto de extension futuro
  (bearer/OAuth/mTLS) sin cambiar el contrato de `McpGateway`.
- Una caida abrupta del proceso puede dejar un mensaje `STREAMING`; la reconciliacion de generaciones
  huerfanas queda pendiente de hardening operativo.

## Secretos

Nunca guardar `.env`, contrasenas, claves de proveedores o `CREDENTIAL_MASTER_KEY` en Git.
Produccion debe inyectarlos desde un gestor de secretos y rotarlos de forma independiente.

## Proveedores y credenciales

- Las claves de proveedor se cifran con AES-256-GCM, nonce aleatorio, tag autenticado, AAD y
  version de clave. La clave maestra debe decodificar a 32 bytes y el backend falla si no existe.
- La API solo devuelve una pista de cuatro caracteres. Ciphertext, nonce, clave y cuerpos de error
  externos no se incluyen en respuestas, logs, metricas ni auditoria.
- Toda lectura de conexion o modelo filtra simultaneamente por propietario e identificador. El rol
  `ADMIN` no evita este filtro.
- Bases OpenAI-compatible y Ollama requieren allowlist exacta. HTTP necesita autorizacion separada;
  redirects, link-local/metadata y bases con userinfo/query/fragment se rechazan.
- Las respuestas de proveedor tienen timeout y limite de bytes. Sprint 2 no aplica reintentos
  automaticos a pruebas de conexion para evitar duplicar solicitudes potencialmente cobrables.

La version de clave prepara rotacion, pero todavía no existe un job de recifrado ni soporte para
varias claves simultaneas. La ventana entre validacion DNS y conexion es un riesgo residual que
debe endurecerse antes de aceptar dominios controlados por terceros fuera de una allowlist estable.

## Chat y contenido generado

- Todas las operaciones filtran `conversation_id` junto con `owner_id`; los IDs ajenos producen 404.
- Sólo una generacion puede estar activa por conversacion, tanto en memoria como mediante indice
  parcial en PostgreSQL.
- El historial y las respuestas tienen limites de mensajes/caracteres. Prompts, deltas y cuerpos de
  proveedor no se registran en auditoria ni logs operativos.
- Request IDs y razones terminales se acotan antes de persistir. Las credenciales se descifran sólo
  al preparar el cliente seleccionado y el arreglo mutable se limpia inmediatamente.
- El render Markdown escapa HTML y atributos, permite enlaces sólo `http`, `https` y `mailto`, y
  Angular vuelve a sanitizar el resultado enlazado a `innerHTML`.
- SSE es mismo-origen, exige sesion y CSRF, devuelve `Cache-Control: no-store`, heartbeats y cancela
  el proveedor ante desconexion. Nginx desactiva buffering para `/api`.

## Documentos (RAG)

- `POST /api/documents` exige sesion y CSRF, igual que el resto de escrituras autenticadas.
- Tamano maximo configurable (`MAX_UPLOAD_BYTES`, 25 MiB por defecto) aplicado en dos capas: el
  limite de multipart de Spring MVC y una lectura acotada (`readNBytes`) en el servicio que nunca
  vuelca un stream sin limite a memoria.
- El tipo de archivo se valida por MIME real (deteccion vía `Tika`, no el `Content-Type` declarado
  por el cliente) cruzado contra la extension declarada; un binario disfrazado con una extension
  permitida se rechaza porque su MIME detectado no coincide con ninguno de los permitidos para esa
  extension.
- El nombre de archivo original solo se persiste como metadata saneada (nunca se usa para resolver
  rutas); el nombre fisico en disco es siempre un UUID generado por el servidor.
- Los archivos `.docx` (unico formato basado en ZIP admitido) pasan por un guardia anti zip-bomb que
  descomprime con un contador acumulado y aborta sobre 100 MB inflados o 2000 entradas, sin confiar
  en el tamano declarado por el archivo.
- El hash SHA-256 del contenido hace que volver a subir el mismo archivo para el mismo propietario
  sea idempotente (devuelve el documento existente) en vez de duplicarlo.
- Todas las operaciones sobre documentos filtran por `owner_id`; un documento de otro propietario
  responde `404`, nunca `403`, para no filtrar su existencia.
- Diferido a la futura extraccion de texto (todavia sin aprobar): proteccion XXE al parsear XML/
  OOXML, limites de paginas/caracteres/chunks, timeout de extraccion. Antivirus sigue siendo
  opcional segun la especificacion.
