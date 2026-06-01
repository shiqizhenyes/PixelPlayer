#!/usr/bin/env node
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';
import { DEFAULT_DB_PATH, openWriteDb } from './db.js';
import * as fmt from './format.js';
import * as core from './query-core.js';

// ── Tool definitions ──────────────────────────────────────────────────────────

const TOOLS = [
  {
    name: 'kg_overview',
    description:
      'Call this first when entering an unfamiliar project or before planning broad changes. ' +
      'Returns node/edge counts by type, architectural layers, and the top hub nodes by degree.',
    inputSchema: {
      type: 'object' as const,
      properties: {
        limit: {
          type: 'number',
          description: 'Max hub nodes to show (default 10)',
        },
      },
    },
  },
  {
    name: 'kg_search',
    description:
      'Finds relevant files/components by concept using full-text search. ' +
      'CRITICAL: Check the "Connections between search results" in the output. ' +
      'If a result has no connections to the core files, it is isolated or experimental. ' +
      'Do NOT assume or hallucinate connections that are not explicitly listed in the output!',
    inputSchema: {
      type: 'object' as const,
      required: ['query'],
      properties: {
        query: { type: 'string', description: 'Search terms or phrase' },
        type: {
          type: 'string',
          enum: ['file', 'resource', 'module'],
          description: 'Optional node type filter',
        },
        limit: { type: 'number', description: 'Max results (default 15)' },
      },
    },
  },
  {
    name: 'kg_node',
    description:
      'Inspects a node\'s details, members, and connected edges. ' +
      'CRITICAL: Use the incoming (← called-by) and outgoing (→ calls) lists to verify ' +
      'real dependencies. If another file has no edge to this node, they are completely ' +
      'disconnected — do NOT assume or invent a connection between them!',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id (usually a file path)' },
        limit: {
          type: 'number',
          description: 'Max edges per direction (default 15)',
        },
      },
    },
  },
  {
    name: 'kg_dependents',
    description:
      'Call this before editing a file to see what may depend on it (impact analysis). ' +
      'Returns nodes that have an edge pointing TO the given node.',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id to analyse' },
        limit: { type: 'number', description: 'Max results (default 20)' },
      },
    },
  },
  {
    name: 'kg_dependencies',
    description:
      'Use this to understand what a file or component relies on. ' +
      'Returns nodes that the given node has an edge pointing TO.',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id to analyse' },
        limit: { type: 'number', description: 'Max results (default 20)' },
      },
    },
  },
  {
    name: 'kg_neighbors',
    description:
      'Explore the local neighbourhood of a node with configurable depth and direction. ' +
      'Useful for understanding a component\'s immediate context without loading full files.',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id' },
        direction: {
          type: 'string',
          enum: ['in', 'out', 'both'],
          description: 'Edge direction (default: both)',
        },
        edgeType: {
          type: 'string',
          description: 'Filter by edge type (calls, imports, depends_on, contains)',
        },
        depth: {
          type: 'number',
          description: 'Traversal depth 1–4 (default 1)',
        },
        limit: { type: 'number', description: 'Max results (default 30)' },
      },
    },
  },
  {
    name: 'kg_path',
    description:
      'Use this to understand how two components are connected. ' +
      'Finds the shortest undirected path between two nodes in the graph.',
    inputSchema: {
      type: 'object' as const,
      required: ['sourceId', 'targetId'],
      properties: {
        sourceId: { type: 'string', description: 'Starting node id' },
        targetId: { type: 'string', description: 'Ending node id' },
        maxDepth: {
          type: 'number',
          description: 'Max path length (default 6, max 8)',
        },
      },
    },
  },
  {
    name: 'kg_incremental_update',
    description:
      'Re-scans a specific list of edited files and updates their nodes and edges in SQLite immediately. ' +
      'Always call this after you modify any codebase files so the knowledge graph is kept 100% in sync.',
    inputSchema: {
      type: 'object' as const,
      required: ['filePaths'],
      properties: {
        filePaths: {
          type: 'array',
          items: { type: 'string' },
          description: 'List of relative file paths that were modified',
        },
      },
    },
  },
  {
    name: 'kg_annotate_node',
    description:
      'Dynamically adds custom annotations, summaries, or knowledge metadata to an existing node. ' +
      'Use this to write back active learnings, quirks, and debug logs about components.',
    inputSchema: {
      type: 'object' as const,
      required: ['id'],
      properties: {
        id: { type: 'string', description: 'Node id to annotate' },
        summary: { type: 'string', description: 'Optional new summary description override' },
        tags: { type: 'string', description: 'Optional comma-separated list of tags' },
        complexity: {
          type: 'string',
          enum: ['simple', 'moderate', 'complex'],
          description: 'Optional complexity override',
        },
        knowledgeMeta: {
          type: 'object',
          description: 'Optional JSON object containing structured knowledge (e.g. quirks, debug notes, rules)',
        },
      },
    },
  },
  {
    name: 'kg_add_concept',
    description:
      'Creates a new dynamic high-level concept node and optionally connects it to other files/nodes.',
    inputSchema: {
      type: 'object' as const,
      required: ['id', 'name', 'summary'],
      properties: {
        id: { type: 'string', description: 'Unique concept node ID (e.g., concept:crossfade)' },
        name: { type: 'string', description: 'Name of the concept' },
        summary: { type: 'string', description: 'Detailed technical description of this concept' },
        tags: {
          type: 'array',
          items: { type: 'string' },
          description: 'Optional list of tags for this concept',
        },
        connectedNodes: {
          type: 'array',
          items: {
            type: 'object',
            required: ['targetId', 'edgeType'],
            properties: {
              targetId: { type: 'string', description: 'Target node ID connected to this concept' },
              edgeType: { type: 'string', description: 'Edge type (e.g., contains_flow, dynamic, related)' },
              description: { type: 'string', description: 'Optional relationship description' },
            },
          },
          description: 'Optional list of connections to establish',
        },
      },
    },
  },
  {
    name: 'kg_register_interaction',
    description:
      'Registers co-edit or usage-based interaction between a list of nodes, strengthening their logical coupling.',
    inputSchema: {
      type: 'object' as const,
      required: ['nodeIds'],
      properties: {
        nodeIds: {
          type: 'array',
          items: { type: 'string' },
          description: 'Array of at least 2 node IDs that were modified or analyzed together',
        },
        description: { type: 'string', description: 'Optional description of the interaction/task solved' },
      },
    },
  },
];

// ── Server setup ──────────────────────────────────────────────────────────────

const server = new Server(
  { name: 'pixelplayer-kg', version: '0.1.0' },
  { capabilities: { tools: {} } },
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: TOOLS }));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args = {} } = request.params;
  const dbPath = DEFAULT_DB_PATH;

  const text = (t: string) => ({
    content: [{ type: 'text' as const, text: t }],
  });

  try {
    switch (name) {
      case 'kg_overview': {
        const data = core.overview({ dbPath, limit: args['limit'] as number | undefined });
        return text(fmt.formatOverview(data));
      }

      case 'kg_search': {
        const query = String(args['query'] ?? '');
        if (!query) return text('Error: query is required');
        const data = core.search(query, {
          dbPath,
          limit: args['limit'] as number | undefined,
          type: args['type'] as string | undefined,
        });
        return text(fmt.formatSearch(data, query));
      }

      case 'kg_node': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');
        const data = core.node(id, { dbPath, limit: args['limit'] as number | undefined });
        return text(fmt.formatNode(data));
      }

      case 'kg_dependents': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');
        const data = core.dependents(id, { dbPath, limit: args['limit'] as number | undefined });
        return text(fmt.formatDependents(data, id));
      }

      case 'kg_dependencies': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');
        const data = core.dependencies(id, { dbPath, limit: args['limit'] as number | undefined });
        return text(fmt.formatDependencies(data, id));
      }

      case 'kg_neighbors': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');
        const direction = (['in', 'out', 'both'] as const).find(
          (d) => d === args['direction'],
        );
        const data = core.neighbors(id, {
          dbPath,
          limit: args['limit'] as number | undefined,
          direction,
          edgeType: args['edgeType'] as string | undefined,
          depth: args['depth'] as number | undefined,
        });
        return text(fmt.formatNeighbors(data, id));
      }

      case 'kg_path': {
        const sourceId = String(args['sourceId'] ?? '');
        const targetId = String(args['targetId'] ?? '');
        if (!sourceId || !targetId)
          return text('Error: sourceId and targetId are required');
        const data = core.path(sourceId, targetId, {
          dbPath,
          maxDepth: args['maxDepth'] as number | undefined,
        });
        return text(fmt.formatPath(data));
      }

      case 'kg_incremental_update': {
        const filePaths = (args['filePaths'] ?? []) as string[];
        if (filePaths.length === 0) return text('Error: filePaths is required');

        try {
          const { incrementalScan } = await import('./incremental-scanner.js');
          await incrementalScan(filePaths, dbPath);
          return text(`Successfully performed incremental scan and updated the graph for ${filePaths.length} files.`);
        } catch (err) {
          return text(`Error during incremental scan: ${err instanceof Error ? err.message : String(err)}`);
        }
      }

      case 'kg_annotate_node': {
        const id = String(args['id'] ?? '');
        if (!id) return text('Error: id is required');

        const db = openWriteDb(dbPath);
        try {
          const nodeExists = db.prepare('SELECT 1 FROM nodes WHERE id = ?').get(id);
          if (!nodeExists) return text(`Error: node "${id}" not found in knowledge graph`);

          const existing = db.prepare('SELECT * FROM agent_annotations WHERE node_id = ?').get(id) as {
            summary: string | null;
            tags: string | null;
            complexity: string | null;
            knowledge_meta_json: string | null;
          } | undefined;

          const summary = args['summary'] !== undefined ? String(args['summary']) : (existing?.summary ?? null);
          const tags = args['tags'] !== undefined ? String(args['tags']) : (existing?.tags ?? null);
          const complexity = args['complexity'] !== undefined ? String(args['complexity']) : (existing?.complexity ?? null);
          
          let knowledgeMetaJson: string | null = null;
          if (args['knowledgeMeta'] !== undefined) {
            knowledgeMetaJson = JSON.stringify(args['knowledgeMeta']);
          } else if (existing?.knowledge_meta_json) {
            knowledgeMetaJson = existing.knowledge_meta_json;
          }

          db.prepare(
            `INSERT OR REPLACE INTO agent_annotations (node_id, summary, tags, complexity, knowledge_meta_json)
             VALUES (?, ?, ?, ?, ?)`
          ).run(id, summary, tags, complexity, knowledgeMetaJson);

          db.prepare("INSERT OR REPLACE INTO graph_sync_metadata(key, value) VALUES ('overlay_dirty_flag', '1')").run();

          return text(`Successfully annotated node "${id}".`);
        } finally {
          db.close();
        }
      }

      case 'kg_add_concept': {
        const id = String(args['id'] ?? '');
        const name = String(args['name'] ?? '');
        const summary = String(args['summary'] ?? '');
        if (!id || !name || !summary) return text('Error: id, name, and summary are required');

        const db = openWriteDb(dbPath);
        try {
          const tags = Array.isArray(args['tags']) ? args['tags'].join(',') : 'concept';
          const layer = 'Concept';

          db.prepare(
            `INSERT OR REPLACE INTO nodes (id, type, name, summary, tags, layer, complexity, degree_in, degree_out)
             VALUES (?, 'concept', ?, ?, ?, ?, 'simple', 0, 0)`
          ).run(id, name, summary, tags, layer);

          const connections = (args['connectedNodes'] ?? []) as Array<{
            targetId: string;
            edgeType: string;
            description?: string;
          }>;

          if (connections.length > 0) {
            const insertEdge = db.prepare(
              `INSERT OR REPLACE INTO dynamic_edges (source, target, type, direction, weight, description)
               VALUES (?, ?, ?, 'forward', 1.0, ?)`
            );
            for (const conn of connections) {
              insertEdge.run(id, conn.targetId, conn.edgeType, conn.description ?? null);
            }
          }

          db.prepare("INSERT OR REPLACE INTO graph_sync_metadata(key, value) VALUES ('overlay_dirty_flag', '1')").run();

          return text(`Successfully added concept node "${id}" with ${connections.length} connections.`);
        } finally {
          db.close();
        }
      }

      case 'kg_register_interaction': {
        const nodeIds = (args['nodeIds'] ?? []) as string[];
        const description = String(args['description'] ?? 'Interaction based on task execution');
        if (nodeIds.length < 2) return text('Error: at least 2 nodeIds are required to register an interaction');

        const db = openWriteDb(dbPath);
        try {
          const insertEdge = db.prepare(
            `INSERT OR REPLACE INTO dynamic_edges (source, target, type, direction, weight, description)
             VALUES (?, ?, 'related', 'bidirectional', 0.8, ?)`
          );

          let count = 0;
          for (let i = 0; i < nodeIds.length; i++) {
            for (let j = i + 1; j < nodeIds.length; j++) {
              const src = nodeIds[i]!;
              const tgt = nodeIds[j]!;

              const srcExists = db.prepare('SELECT 1 FROM nodes WHERE id = ?').get(src);
              const tgtExists = db.prepare('SELECT 1 FROM nodes WHERE id = ?').get(tgt);
              if (srcExists && tgtExists) {
                insertEdge.run(src, tgt, description);
                count++;
              }
            }
          }

          db.prepare("INSERT OR REPLACE INTO graph_sync_metadata(key, value) VALUES ('overlay_dirty_flag', '1')").run();

          return text(`Successfully registered ${count} interaction edges between ${nodeIds.length} nodes.`);
        } finally {
          db.close();
        }
      }

      default:
        return text(`Unknown tool: ${name}`);
    }
  } catch (err) {
    return text(
      `Error: ${err instanceof Error ? err.message : String(err)}`,
    );
  }
});

// ── Start ─────────────────────────────────────────────────────────────────────

const transport = new StdioServerTransport();
server.connect(transport).catch((err) => {
  process.stderr.write(`MCP server error: ${err}\n`);
  process.exit(1);
});
