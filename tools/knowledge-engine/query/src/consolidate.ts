import { readFileSync, writeFileSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { openWriteDb, DEFAULT_DB_PATH, DEFAULT_JSON_PATH } from './db.js';
import { validateGraph } from '../../core/dist/schema.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

interface NodeData {
  id: string;
  type: string;
  name: string;
  file_path: string | null;
  summary: string | null;
  tags: string | null;
  layer: string | null;
  complexity: string | null;
}

interface EdgeRow {
  source: string;
  target: string;
  type: string;
  direction: string;
  weight: number | null;
  description: string | null;
}

interface GraphMember {
  node_id: string;
  kind: string;
  name: string;
}

export function consolidate(
  jsonPath = DEFAULT_JSON_PATH,
  dbPath = DEFAULT_DB_PATH,
): void {
  console.log(`🚀 Starting Graph Consolidator...`);
  console.log(`  JSON Path: ${jsonPath}`);
  console.log(`  DB Path: ${dbPath}`);

  const baseJsonRaw = readFileSync(jsonPath, 'utf-8');
  const baseGraph = JSON.parse(baseJsonRaw);

  const db = openWriteDb(dbPath);
  try {
    const sqlNodes = db.prepare(`SELECT * FROM nodes`).all() as unknown as NodeData[];
    
    const sqlMembers = db.prepare(`SELECT * FROM node_members`).all() as unknown as GraphMember[];
    const membersMap = new Map<string, Array<{ kind: string; name: string }>>();
    for (const m of sqlMembers) {
      if (!membersMap.has(m.node_id)) membersMap.set(m.node_id, []);
      membersMap.get(m.node_id)!.push({ kind: m.kind, name: m.name });
    }

    const sqlAnnotations = db.prepare(`SELECT * FROM agent_annotations`).all() as unknown as Array<{
      node_id: string;
      summary: string | null;
      tags: string | null;
      complexity: string | null;
      knowledge_meta_json: string | null;
    }>;
    const annotationsMap = new Map(sqlAnnotations.map(a => [a.node_id, a]));

    const mergedNodes = sqlNodes.map(node => {
      const classes: string[] = [];
      const functions: string[] = [];
      for (const m of membersMap.get(node.id) ?? []) {
        if (m.kind === 'class') classes.push(m.name);
        if (m.kind === 'function') functions.push(m.name);
      }

      const n: any = {
        id: node.id,
        type: node.type,
        name: node.name,
        summary: node.summary ?? '',
        tags: node.tags ? node.tags.split(',') : [],
        complexity: node.complexity ?? 'moderate',
      };

      if (node.file_path) n.filePath = node.file_path;
      if (classes.length > 0) n.classes = classes;
      if (functions.length > 0) n.functions = functions;

      const ann = annotationsMap.get(node.id);
      if (ann) {
        if (ann.summary) n.summary = ann.summary;
        if (ann.tags) n.tags = ann.tags.split(',');
        if (ann.complexity) n.complexity = ann.complexity;
        if (ann.knowledge_meta_json) {
          n.knowledgeMeta = JSON.parse(ann.knowledge_meta_json);
        }
      }

      return n;
    });

    const sqlEdges = db.prepare(`SELECT * FROM edges`).all() as unknown as EdgeRow[];
    const sqlDynamicEdges = db.prepare(`SELECT * FROM dynamic_edges`).all() as unknown as EdgeRow[];
    const allEdges = [...sqlEdges, ...sqlDynamicEdges];

    const mergedEdges = allEdges.map(e => {
      const edge: any = {
        source: e.source,
        target: e.target,
        type: e.type,
        direction: e.direction ?? 'forward',
        weight: e.weight ?? 0.5,
      };
      if (e.description) edge.description = e.description;
      return edge;
    });

    const finalGraph = {
      version: baseGraph.version ?? '1.0.0',
      kind: baseGraph.kind ?? 'codebase',
      project: baseGraph.project,
      nodes: mergedNodes,
      edges: mergedEdges,
      layers: baseGraph.layers ?? [],
      tour: baseGraph.tour ?? [],
    };

    console.log(`🔍 Validating merged graph using Zod Schema...`);
    const validation = validateGraph(finalGraph);
    if (!validation.success) {
      throw new Error(`Graph validation failed: ${validation.fatal || validation.errors?.join('\n')}`);
    }

    console.log(`💾 Persisting unified graph to ${jsonPath}...`);
    const formattedJson = JSON.stringify(validation.data, null, 2);
    writeFileSync(jsonPath, formattedJson, 'utf-8');

    console.log(`🧹 Clearing SQLite dynamic overlays...`);
    db.prepare('DELETE FROM agent_annotations').run();
    db.prepare('DELETE FROM dynamic_edges').run();

    const newHash = createHash('sha256').update(formattedJson).digest('hex');
    const newMtime = statSync(jsonPath).mtime.toISOString();
    
    db.prepare("INSERT OR REPLACE INTO graph_sync_metadata (key, value) VALUES ('canonical_json_hash', ?)").run(newHash);
    db.prepare("INSERT OR REPLACE INTO graph_sync_metadata (key, value) VALUES ('canonical_json_mtime', ?)").run(newMtime);
    db.prepare("INSERT OR REPLACE INTO graph_sync_metadata (key, value) VALUES ('overlay_dirty_flag', '0')").run();

    console.log(`🎉 Graph consolidation completed successfully and DB synced.`);
  } finally {
    db.close();
  }
}
