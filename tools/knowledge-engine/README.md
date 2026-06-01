# 🧠 Self-Evolving GraphRAG Knowledge Engine & Control Console

Este directorio contiene el motor de conocimiento en forma de **Knowledge Graph (KG) y GraphRAG auto-evolutivo** para **PixelPlayer**. 

El sistema utiliza una arquitectura avanzada desacoplada en dos capas:
1. **Core Inmutable (`knowledge-graph.json`):** El grafo maestro sincronizado con Git que representa el análisis estático de la arquitectura (clases, dependencias, DI Hilt).
2. **Overlays Dinámicos (SQLite `graph.db`):** Tablas staged donde los agentes de código registran anotaciones, abstracciones conceptuales y pesos en tiempo real sin modificar el JSON core ni ensuciar los diffs de Git durante la sesión de codificación.

---

## 🌟 Tabla de Contenidos
1. [🛠️ Scripts de Configuración y Compilación](#-scripts-de-configuración-y-compilación)
2. [💻 Manual de Uso del CLI (`pnpm kg`)](#-manual-de-uso-del-cli-pnpm-kg)
3. [🤖 Catálogo de Herramientas MCP para Agentes](#-catálogo-de-herramientas-mcp-para-agentes)
4. [🎛️ Consola Gráfica de Auditoría y Evolución](#%EF%B8%8F-consola-gráfica-de-auditoría-y-evolución)
5. [🔄 Flujo de Trabajo Típico Recomendado](#-flujo-de-trabajo-típico-recomendado)

---

## 🛠️ Scripts de Configuración y Compilación

Desde la raíz de `tools/knowledge-engine`, puedes ejecutar los siguientes scripts de `npm` / `pnpm` para gestionar el estado del motor:

| Comando | Descripción |
| :--- | :--- |
| `pnpm run scan` | Escanea el código fuente de **PixelPlayer** y genera el JSON maestro `knowledge-graph.json`. |
| `pnpm run build` | Compila todos los workspaces (`core`, `dashboard`, `query`) para producción. |
| `pnpm run test` | Ejecuta las pruebas unitarias e integración de los workspaces. |
| `pnpm kg:compile` | Compila los archivos TypeScript del servidor backend `query` rápidamente. |
| `pnpm kg:build` | Reconstruye la base de datos SQLite `graph.db` desde el JSON maestro, calculando e inyectando hashes de control. |
| `pnpm run validate` | Ejecuta tests estructurales de consistencia del grafo. |
| `pnpm run validate:factual` | Corre un validador de veracidad e integridad semántica sobre los datos del grafo. |

---

## 💻 Manual de Uso del CLI (`pnpm kg`)

El CLI `kg` permite consultar el grafo completo y los overlays dinámicos directamente desde la terminal.

### Uso General:
```bash
pnpm kg <comando> [opciones]
```

### Opciones Globales:
*   `--db <path>`: Ruta alternativa al archivo SQLite `graph.db` (por defecto: `.understand-anything/graph.db`).
*   `--json`: Retorna la respuesta en formato JSON crudo para integración con scripts en lugar de texto enriquecido formateado.
*   `--limit <n>`: Limita el número de resultados retornados.

---

### Comandos del CLI:

#### 1. Construcción de la Base de Datos
```bash
pnpm kg build [ruta/a/knowledge-graph.json]
```
*   **Qué hace:** Genera la base de datos relacional SQLite `graph.db` a partir del JSON. Calcula de forma automática el SHA-256 del archivo JSON para sincronización.

#### 2. Consolidación de Overlays en JSON
```bash
pnpm kg consolidate [ruta/a/knowledge-graph.json]
```
*   **Qué hace:** Fusiona las anotaciones temporales de los agentes (`agent_annotations`) y nuevas aristas dinámicas (`dynamic_edges`) con el núcleo estructurado del JSON maestro, valida el resultado con Zod para evitar corrupción y actualiza `knowledge-graph.json` físicamente. Luego, vacía las tablas temporales en SQLite y sincroniza hashes.

#### 3. Vista General del Proyecto
```bash
pnpm kg overview [--limit <n>]
```
*   **Qué hace:** Muestra estadísticas del proyecto: número total de nodos, relaciones, desglose por capas arquitectónicas y los nodos con mayor grado de acoplamiento (hubs).

#### 4. Búsqueda Semántica de Nodos
```bash
pnpm kg search "<término>" [--type <file|resource|module>] [--limit <n>]
```
*   **Qué hace:** Realiza una búsqueda de texto completo (FTS) sobre nodos del grafo. Muestra la jerarquía de los archivos encontrados y las dependencias lógicas existentes entre ellos.

#### 5. Inspección de Nodo Individual
```bash
pnpm kg node <id-del-nodo>
```
*   **Qué hace:** Muestra información exhaustiva de un archivo o nodo: su propósito, dependencias entrantes (quién lo llama) y dependencias salientes (a quién llama).
*   *Ejemplo:* `pnpm kg node app/src/main/java/com/theveloper/pixelplay/ui/player/PlayerViewModel.kt`

#### 6. Análisis de Impacto (Dependientes)
```bash
pnpm kg dependents <id-del-nodo> [--limit <n>]
```
*   **Qué hace:** Lista todos los módulos y archivos que dependen de este nodo. **Debe ejecutarse siempre antes de hacer refactoring** para prevenir roturas en cascada.

#### 7. Análisis de Dependencias
```bash
pnpm kg deps <id-del-nodo> [--limit <n>]
```
*   **Qué hace:** Lista los archivos y conceptos de los cuales depende este nodo.

#### 8. Consulta de Vecindad
```bash
pnpm kg neighbors <id-del-nodo> [--dir in|out|both] [--type <tipoArista>] [--depth <depth>]
```
*   **Qué hace:** Explora los nodos vecinos inmediatos con profundidad configurable de 1 a 4. Filtra por tipo de arista (`calls`, `imports`, `depends_on`, `contains`).

#### 9. Ruta de Conexión entre Componentes
```bash
pnpm kg path <id-nodo-origen> <id-nodo-destino> [--max-depth <n>]
```
*   **Qué hace:** Encuentra el camino más corto de acoplamiento entre dos archivos en el grafo de dependencias para entender cómo se comunican.

---

## 🤖 Catálogo de Herramientas MCP para Agentes

Si estás usando un agente compatible con el **Model Context Protocol (MCP)** (como *Claude Code*, *Antigravity*, o similar), el agente tendrá acceso a estas herramientas de forma automática en su caja de herramientas.

### Herramientas de Consulta y Lectura (RAG Seguro):
1.  **`kg_overview`**: Retorna estadísticas de capas arquitectónicas y hubs.
2.  **`kg_search`**: Búsqueda textual y relaciones cruzadas para evitar alucinaciones de código.
3.  **`kg_node`**: Devuelve detalles atómicos y aristas reales (entrantes y salientes).
4.  **`kg_dependents`**: Análisis de impacto e influencia arquitectónica.
5.  **`kg_dependencies`**: Lista de dependencias directas del archivo.
6.  **`kg_neighbors`**: Travesía local de vecindarios con profundidad.
7.  **`kg_path`**: Traza el acoplamiento y ruta de conexión entre dos archivos.

### Herramientas de Escritura y Evolución (Self-Evolving):

#### 8. `kg_incremental_update`
*   **Propósito:** Actualiza en milisegundos las definiciones del grafo para los archivos locales modificados mediante análisis estático de AST (importaciones, inyecciones Hilt DI, llamadas).
*   **Argumentos:**
    *   `filePaths`: Lista de strings con rutas relativas de los archivos editados.
*   **Cuándo usar:** Siempre que el agente modifique o cree archivos en el código.

#### 9. `kg_annotate_node`
*   **Propósito:** Registra aprendizajes activos, quirks de comportamiento y notas técnicas directamente en el nodo.
*   **Argumentos:**
    *   `id`: ID del nodo (ruta del archivo).
    *   `summary` (opcional): Descripción resumida que sobrescribe la original.
    *   `tags` (opcional): Tags separados por comas para clasificar el nodo.
    *   `complexity` (opcional): `simple` \| `moderate` \| `complex`.
    *   `knowledgeMeta` (opcional): JSON con estructuras personalizadas de aprendizaje (ej. reglas de mantenimiento, advertencias de bugs).

#### 10. `kg_add_concept`
*   **Propósito:** Define un nuevo nodo conceptual abstracto de alto nivel (como patrones de diseño o flujos de audio) y lo conecta a archivos de implementación reales en el grafo.
*   **Argumentos:**
    *   `id`: ID del concepto (ej: `concept:wear-communication`).
    *   `name`: Nombre de la abstracción.
    *   `summary`: Descripción detallada.
    *   `connectedNodes` (opcional): Lista de aristas dinámicas a enlazar.

#### 11. `kg_register_interaction`
*   **Propósito:** Modifica los pesos y fortalece el acoplamiento lógico de un conjunto de archivos analizados o co-editados en conjunto.
*   **Argumentos:**
    *   `nodeIds`: Mínimo 2 IDs de nodos modificados conjuntamente en una tarea.
    *   `description` (opcional): Razón del acoplamiento.

---

## 🎛️ Consola Gráfica de Auditoría y Evolución

El sistema incluye una interfaz web premium de control de evolución en tiempo real que previene la degradación de datos mediante la revisión humana y un "Juez de Arquitectura" con IA.

### Cómo Iniciar el Dashboard:
Desde la carpeta raíz del proyecto, ejecuta:
```bash
pnpm --filter @understand-anything/dashboard run dev
```
La terminal imprimirá un enlace protegido similar a:
`🔑 Dashboard URL: http://127.0.0.1:5173/?token=<TOKEN_DINAMICO>`

### 1. Visor de Grafo Clásico (React Flow)
Abre la URL base con el token. Permite visualizar e interactuar tridimensionalmente con nodos y relaciones del código.

### 2. Consola de Control de Evolución Aislada (Control Center)
Para auditar y aplicar los cambios propuestos por los agentes:
Abre la URL con el parámetro `view=control`:
`http://127.0.0.1:5173/?view=control&token=<TOKEN_DINAMICO>`

#### Funcionalidades en Pantalla:
*   **Consolidar Cambios (`🚀 Consolidar en Core JSON`):** Toma las anotaciones y relaciones temporales staged en SQLite, las valida contra las reglas de esquema Zod para evitar roturas de estructura en Git, las inyecta físicamente en `knowledge-graph.json` y vacía la base temporal.
*   **Auditar con Juez IA (`🤖 Auditar con Juez IA`):** Evalúa heurísticamente si las relaciones y conceptos propuestos por los agentes cumplen con las directrices arquitectónicas de PixelPlayer antes de la consolidación.
*   **Descartar Cambios (`🗑️ Descartar Borradores`):** Borra y vacía permanentemente todos los borradores temporales acumulados en SQLite.

---

## 🔄 Flujo de Trabajo Típico Recomendado

1.  **Inicio de la Sesión:** El agente lee `kg_overview` y `kg_node` para entender la arquitectura y relaciones sin alucinaciones.
2.  **Codificación y Edición:** El agente realiza cambios de código en los archivos de PixelPlayer.
3.  **Registro de Aprendizaje (Evolución):**
    *   El agente ejecuta `kg_incremental_update` sobre los archivos modificados.
    *   El agente documenta problemas detectados, reglas o complejidades con `kg_annotate_node`.
    *   *Los cambios se guardan de forma instantánea en SQLite de manera segura, sin ensuciar los diffs de Git.*
4.  **Auditoría y Cierre:** El desarrollador accede a la **Consola de Evolución** (`?view=control`), revisa los staged propuestos por el agente y presiona **Consolidar en Core JSON**. El JSON maestro se actualiza limpiamente en un solo commit controlado.
