import { useState, useEffect, useCallback } from "react";

interface SyncStatus {
  synced: boolean;
  dbHash: string;
  jsonHash: string;
  isStale: boolean;
  isDirty: boolean;
  annotationCount: number;
  edgeCount: number;
  reason?: string;
}

interface StagedAnnotation {
  node_id: string;
  summary: string | null;
  tags: string | null;
  complexity: string | null;
  knowledge_meta_json: string | null;
}

interface StagedEdge {
  source: string;
  target: string;
  type: string;
  direction: string;
  weight: number;
  description: string | null;
}

function basename(path: string): string {
  const parts = path.split(/[/\\]/);
  return parts[parts.length - 1] || path;
}

export default function ControlCenter() {
  const [status, setStatus] = useState<SyncStatus | null>(null);
  const [annotations, setAnnotations] = useState<StagedAnnotation[]>([]);
  const [edges, setEdges] = useState<StagedEdge[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [message, setMessage] = useState<{ text: string; type: "success" | "error" | "info" } | null>(null);
  const [expandedNode, setExpandedNode] = useState<string | null>(null);
  const [auditedIds, setAuditedIds] = useState<Record<string, { plausible: boolean; score: number; reason: string }>>({});
  const [auditing, setAuditing] = useState(false);

  // Extract access token from URL
  const urlParams = new URLSearchParams(window.location.search);
  const token = urlParams.get("token") ?? "";

  const fetchData = useCallback(async () => {
    if (!token) {
      setMessage({ text: "Error: No access token provided in URL.", type: "error" });
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      // Fetch synchronization status
      const statusRes = await fetch(`/api/sync-status?token=${token}`);
      if (!statusRes.ok) throw new Error(`Failed to fetch sync status: ${statusRes.statusText}`);
      const statusData = await statusRes.json();
      setStatus(statusData);

      // Fetch dynamic overlay changes
      const overlayRes = await fetch(`/api/overlays?token=${token}`);
      if (!overlayRes.ok) throw new Error(`Failed to fetch overlays: ${overlayRes.statusText}`);
      const overlayData = await overlayRes.json();
      setAnnotations(overlayData.annotations ?? []);
      setEdges(overlayData.edges ?? []);
    } catch (err) {
      setMessage({
        text: err instanceof Error ? err.message : "Unexpected error fetching dashboard data",
        type: "error",
      });
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleConsolidate = async () => {
    if (!confirm("Are you sure you want to consolidate staged overlays? This will physically update knowledge-graph.json and clear the SQLite temporary tables.")) {
      return;
    }

    setActionLoading(true);
    setMessage({ text: "Consolidating dynamic overlays into knowledge-graph.json...", type: "info" });

    try {
      const res = await fetch(`/api/consolidate?token=${token}`, { method: "POST" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "Failed to consolidate");

      setMessage({ text: "🎉 Success! Graph consolidated and master JSON updated.", type: "success" });
      setAuditedIds({});
      await fetchData();
    } catch (err) {
      setMessage({
        text: err instanceof Error ? err.message : "Error during graph consolidation",
        type: "error",
      });
    } finally {
      setActionLoading(false);
    }
  };

  const handleDiscard = async () => {
    if (!confirm("⚠️ CAUTION: Are you sure you want to discard all staged overlays? This will permanently wipe annotations and dynamic relations from the SQLite database. This action cannot be undone.")) {
      return;
    }

    setActionLoading(true);
    setMessage({ text: "Wiping staged overlays from SQLite database...", type: "info" });

    try {
      const res = await fetch(`/api/discard?token=${token}`, { method: "POST" });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error ?? "Failed to discard overlays");

      setMessage({ text: "🧹 Staged overlays cleared successfully.", type: "success" });
      setAuditedIds({});
      await fetchData();
    } catch (err) {
      setMessage({
        text: err instanceof Error ? err.message : "Error during overlay wipe",
        type: "error",
      });
    } finally {
      setActionLoading(false);
    }
  };

  const handleAuditIA = async () => {
    if (edges.length === 0) {
      setMessage({ text: "No staged dynamic edges to audit.", type: "info" });
      return;
    }

    setAuditing(true);
    setMessage({ text: "Running AI architectural audits on dynamic relations...", type: "info" });

    try {
      const newAudited: Record<string, any> = { ...auditedIds };
      for (const edge of edges) {
        const edgeKey = `${edge.source}->${edge.target}`;
        const res = await fetch(`/api/validate-ia?token=${token}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(edge)
        });
        const data = await res.json();
        newAudited[edgeKey] = {
          plausible: data.plausible,
          score: data.score,
          reason: data.reason
        };
      }
      setAuditedIds(newAudited);
      setMessage({ text: "✓ AI architectural audit completed successfully.", type: "success" });
    } catch (err) {
      setMessage({ text: "Failed to perform AI audit.", type: "error" });
    } finally {
      setAuditing(false);
    }
  };

  if (!token) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#0d0e12] text-white">
        <div className="rounded-xl border border-red-500/20 bg-red-950/10 p-8 text-center backdrop-blur-md max-w-md">
          <h2 className="text-2xl font-bold text-red-400 mb-4">Acceso Denegado</h2>
          <p className="text-gray-400">
            Falta el token de acceso dinámico en la URL de la Consola. Por favor, abre el Dashboard utilizando el enlace impreso en la terminal.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0b0c10] text-[#e4e6eb] font-sans antialiased selection:bg-purple-500/30">
      {/* Background Glows */}
      <div className="fixed inset-0 pointer-events-none overflow-hidden">
        <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-purple-900/10 rounded-full blur-[120px]" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-cyan-900/10 rounded-full blur-[120px]" />
      </div>

      <div className="relative max-w-6xl mx-auto px-6 py-8">
        {/* Header */}
        <header className="flex flex-col md:flex-row md:items-center justify-between border-b border-gray-800/60 pb-6 mb-8 gap-4">
          <div>
            <div className="flex items-center gap-3">
              <span className="text-2xl">🎛️</span>
              <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-purple-400 to-cyan-400 bg-clip-text text-transparent">
                Consola de Evolución y Auditoría
              </h1>
            </div>
            <p className="text-gray-400 text-sm mt-1">
              Control Panel de GraphRAG para autorizaciones, delta scanning y consolidación local
            </p>
          </div>

          <div className="flex items-center gap-3">
            <a
              href={`/?token=${token}`}
              className="px-4 py-2 text-xs font-semibold rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-200 border border-gray-700/60 transition-all flex items-center gap-1.5"
            >
              <span>📊</span> Ir al Visor Gráfico
            </a>
            <button
              onClick={fetchData}
              disabled={loading || actionLoading}
              className="p-2 text-sm rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-200 border border-gray-700/60 transition-all disabled:opacity-50"
              title="Refrescar datos"
            >
              🔄
            </button>
          </div>
        </header>

        {/* Alerts & Messages */}
        {message && (
          <div
            className={`mb-6 p-4 rounded-xl border flex items-center justify-between transition-all backdrop-blur-md ${
              message.type === "success"
                ? "bg-emerald-950/20 border-emerald-500/25 text-emerald-300"
                : message.type === "error"
                ? "bg-rose-950/20 border-rose-500/25 text-rose-300"
                : "bg-blue-950/20 border-blue-500/25 text-blue-300"
            }`}
          >
            <div className="flex items-center gap-2.5">
              <span>{message.type === "success" ? "✓" : message.type === "error" ? "⚠️" : "ℹ️"}</span>
              <p className="text-sm font-medium">{message.text}</p>
            </div>
            <button
              onClick={() => setMessage(null)}
              className="text-xs font-bold hover:opacity-75 transition-all pl-4 text-gray-400"
            >
              Cerrar
            </button>
          </div>
        )}

        {/* Sync Health Card */}
        {status && (
          <section className="rounded-xl border border-gray-800/80 bg-gray-900/20 backdrop-blur-md p-6 mb-8 grid grid-cols-1 md:grid-cols-4 gap-6 relative overflow-hidden">
            <div className="md:col-span-2 flex flex-col justify-center">
              <span className="text-xs uppercase tracking-wider text-gray-500 font-bold mb-1">
                Estado de Salud del Grafo
              </span>
              <div className="flex items-center gap-3">
                {status.isStale ? (
                  <span className="flex h-3.5 w-3.5 relative">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-3.5 w-3.5 bg-red-500"></span>
                  </span>
                ) : status.isDirty ? (
                  <span className="flex h-3.5 w-3.5 relative">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-3.5 w-3.5 bg-blue-500"></span>
                  </span>
                ) : (
                  <span className="h-3.5 w-3.5 rounded-full bg-emerald-500 inline-block" />
                )}
                <span className="text-xl font-bold">
                  {status.isStale
                    ? "Desalineado (SQLite Desactualizado)"
                    : status.isDirty
                    ? " overlays dinámicos staged (Borrador)"
                    : "Grafo Sincronizado y Puro"}
                </span>
              </div>
              <p className="text-gray-400 text-xs mt-2">
                {status.isStale
                  ? "Se ha detectado una modificación en el JSON core que requiere reconstrucción en SQLite."
                  : status.isDirty
                  ? "Existen anotaciones temporales del agente que necesitan consolidarse en el JSON maestro."
                  : "Todos los modelos y datos están 100% integrados y alineados con Git."}
              </p>
            </div>

            <div className="border-t md:border-t-0 md:border-l border-gray-800/80 pt-4 md:pt-0 md:pl-6 flex flex-col justify-center">
              <span className="text-xs text-gray-500 font-bold mb-0.5">Borradores en SQLite</span>
              <span className="text-3xl font-extrabold text-cyan-400">
                {status.annotationCount + status.edgeCount}
              </span>
              <span className="text-gray-400 text-xs mt-1">
                {status.annotationCount} anotaciones, {status.edgeCount} aristas
              </span>
            </div>

            <div className="border-t md:border-t-0 md:border-l border-gray-800/80 pt-4 md:pt-0 md:pl-6 flex flex-col justify-center">
              <span className="text-xs text-gray-500 font-bold mb-0.5">Hash de Control de JSON</span>
              <span className="text-xs font-mono text-purple-400 select-all truncate" title={status.jsonHash}>
                {status.jsonHash.slice(0, 16)}...
              </span>
              <span className="text-gray-400 text-xs mt-1">SHA-256 Maestro de Git</span>
            </div>
          </section>
        )}

        {/* Loading Spinner */}
        {loading ? (
          <div className="flex flex-col h-64 items-center justify-center gap-3">
            <div className="h-10 w-10 animate-spin rounded-full border-4 border-purple-500/20 border-t-purple-400" />
            <p className="text-gray-400 text-sm">Cargando base de datos relacional...</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Left/Middle Column - Overlay Staged Items */}
            <div className="lg:col-span-2 flex flex-col gap-8">
              {/* Annotations Section */}
              <div className="rounded-xl border border-gray-800/80 bg-gray-900/10 backdrop-blur-md p-6">
                <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
                  <span>📝</span> Anotaciones en Borrador ({annotations.length})
                </h2>

                {annotations.length === 0 ? (
                  <div className="text-center py-10 border border-dashed border-gray-800/60 rounded-lg">
                    <p className="text-gray-500 text-sm">No hay anotaciones temporales de agentes staged en SQLite.</p>
                  </div>
                ) : (
                  <div className="flex flex-col gap-4">
                    {annotations.map((ann) => (
                      <div
                        key={ann.node_id}
                        className="rounded-lg border border-gray-800/60 bg-gray-950/20 p-4 transition-all hover:border-gray-700/60"
                      >
                        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 border-b border-gray-800/40 pb-2.5 mb-2.5">
                          <span className="font-mono text-xs text-purple-400 truncate max-w-full" title={ann.node_id}>
                            {basename(ann.node_id)}
                          </span>
                          {ann.complexity && (
                            <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded bg-cyan-950/40 text-cyan-300 border border-cyan-800/30">
                              {ann.complexity}
                            </span>
                          )}
                        </div>

                        <p className="text-gray-300 text-sm italic mb-3">
                          &ldquo;{ann.summary ?? "Sin descripción override"}&rdquo;
                        </p>

                        {ann.tags && (
                          <div className="flex flex-wrap gap-1.5 mb-3">
                            {ann.tags.split(",").map((tag) => (
                              <span key={tag} className="text-[10px] px-2 py-0.5 rounded bg-gray-800/60 text-gray-400">
                                #{tag}
                              </span>
                            ))}
                          </div>
                        )}

                        {ann.knowledge_meta_json && (
                          <div className="mt-2.5">
                            <button
                              onClick={() => setExpandedNode(expandedNode === ann.node_id ? null : ann.node_id)}
                              className="text-xs text-cyan-400 hover:text-cyan-300 transition-all font-semibold flex items-center gap-1"
                            >
                              <span>{expandedNode === ann.node_id ? "▼" : "▶"}</span>
                              {expandedNode === ann.node_id ? "Ocultar Aprendizaje" : "Mostrar Aprendizaje (Metadata)"}
                            </button>

                            {expandedNode === ann.node_id && (
                              <pre className="mt-2.5 p-3 rounded-md bg-[#050608] border border-gray-900 font-mono text-[11px] text-gray-400 overflow-x-auto">
                                {JSON.stringify(JSON.parse(ann.knowledge_meta_json), null, 2)}
                              </pre>
                            )}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Dynamic Edges Section */}
              <div className="rounded-xl border border-gray-800/80 bg-gray-900/10 backdrop-blur-md p-6">
                <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
                  <span>🔗</span> Relaciones en Borrador ({edges.length})
                </h2>

                {edges.length === 0 ? (
                  <div className="text-center py-10 border border-dashed border-gray-800/60 rounded-lg">
                    <p className="text-gray-500 text-sm">No hay enlaces dinámicos staged en SQLite.</p>
                  </div>
                ) : (
                  <div className="flex flex-col gap-4">
                    {edges.map((edge, idx) => {
                      const edgeKey = `${edge.source}->${edge.target}`;
                      const audit = auditedIds[edgeKey];
                      return (
                        <div
                          key={idx}
                          className="rounded-lg border border-gray-800/60 bg-gray-950/20 p-4 transition-all hover:border-gray-700/60"
                        >
                          <div className="flex flex-wrap items-center gap-2 text-xs font-mono mb-2.5">
                            <span className="text-purple-400" title={edge.source}>{basename(edge.source)}</span>
                            <span className="text-cyan-400 font-bold">➔ [{edge.type}] ➔</span>
                            <span className="text-purple-400" title={edge.target}>{basename(edge.target)}</span>
                          </div>

                          <p className="text-gray-400 text-xs italic mb-2.5">
                            {edge.description ?? "Conexión relacional dinámica generada por el agente."}
                          </p>

                          <div className="flex items-center justify-between border-t border-gray-800/40 pt-2.5 mt-2.5">
                            <span className="text-[10px] text-gray-500">
                              Peso Relacional: <strong className="text-gray-300 font-mono">{edge.weight}</strong>
                            </span>

                            {audit && (
                              <span
                                className={`text-[10px] font-bold px-2 py-0.5 rounded border ${
                                  audit.score >= 4
                                    ? "bg-emerald-950/40 border-emerald-500/20 text-emerald-400"
                                    : "bg-amber-950/40 border-amber-500/20 text-amber-400"
                                }`}
                                title={audit.reason}
                              >
                                Juez IA: {audit.score}/5 ({audit.plausible ? "Aprobado" : "Bajo Score"})
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            {/* Right Column - Actions Panel */}
            <div className="flex flex-col gap-6">
              <div className="rounded-xl border border-gray-800/80 bg-gray-900/10 backdrop-blur-md p-6 sticky top-8">
                <h2 className="text-lg font-bold mb-4 border-b border-gray-800/60 pb-2">
                  Consola de Acciones
                </h2>

                <div className="flex flex-col gap-3">
                  <button
                    onClick={handleConsolidate}
                    disabled={actionLoading || loading || (!status?.isDirty && !status?.isStale)}
                    className="w-full py-3 px-4 rounded-lg bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white font-semibold text-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed shadow-lg shadow-purple-900/20 active:scale-[0.98]"
                  >
                    🚀 Consolidar en Core JSON
                  </button>

                  <button
                    onClick={handleAuditIA}
                    disabled={actionLoading || loading || edges.length === 0 || auditing}
                    className="w-full py-2.5 px-4 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-200 border border-gray-700/60 font-medium text-sm transition-all disabled:opacity-40 active:scale-[0.98]"
                  >
                    {auditing ? "🤖 Auditando..." : "🤖 Auditar con Juez IA"}
                  </button>

                  <button
                    onClick={handleDiscard}
                    disabled={actionLoading || loading || !status?.isDirty}
                    className="w-full py-2.5 px-4 rounded-lg border border-red-500/30 bg-red-950/10 text-red-400 hover:bg-red-950/20 font-medium text-sm transition-all disabled:opacity-40 active:scale-[0.98]"
                  >
                    🗑️ Descartar Borradores
                  </button>
                </div>

                <div className="mt-6 pt-4 border-t border-gray-800/60">
                  <h3 className="text-xs uppercase tracking-wider text-gray-500 font-bold mb-2">
                    Ayuda y Flujo
                  </h3>
                  <ul className="text-gray-400 text-xs flex flex-col gap-2 list-disc pl-4">
                    <li>Las modificaciones staged residen únicamente en SQLite para preservar velocidad y diffs limpios.</li>
                    <li>
                      <strong>Consolidar</strong> unifica la base de datos core con los staged, valida el resultado con Zod y re-escribe el archivo JSON físico.
                    </li>
                    <li>
                      <strong>Auditar</strong> evalúa mediante lógica de arquitectura la veracidad de los nuevos enlaces dinámicos antes de fusionarlos.
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
