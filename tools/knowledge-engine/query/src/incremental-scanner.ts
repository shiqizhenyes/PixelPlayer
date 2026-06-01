import { readFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { openWriteDb, DEFAULT_DB_PATH } from './db.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = join(__dirname, '..', '..', '..');

// Helper to strip block comments, line comments, and string literals
function stripStringsAndComments(content: string): string {
  let cleaned = content.replace(/\/\*[\s\S]*?\*\//g, (match) => match.replace(/[^\r\n]/g, ''));
  cleaned = cleaned.replace(/\/\/.*$/gm, '');
  cleaned = cleaned.replace(/"(\\.|[^"\\])*"/g, '""');
  cleaned = cleaned.replace(/"""[\s\S]*?"""/g, '""""""');
  return cleaned;
}

// Helper to resolve class name to file ID
function resolveClassToFile(
  className: string,
  sourceFileId: string,
  importedClassNames: Set<string>,
  shortClassToFilesMap: Record<string, string[]>,
  classToFileMap: Record<string, string>,
  fileToPackageMap: Record<string, string>,
): string | null {
  const currentPackage = fileToPackageMap[sourceFileId];
  if (currentPackage) {
    const fqcn = `${currentPackage}.${className}`;
    if (classToFileMap[fqcn]) return classToFileMap[fqcn];
  }

  for (const importedClass of importedClassNames) {
    if (importedClass === className) {
      for (const [fqcn, fileId] of Object.entries(classToFileMap)) {
        if (fqcn.endsWith(`.${className}`)) {
          return fileId;
        }
      }
    }
  }

  // Fallback to short class name mapping
  const files = shortClassToFilesMap[className];
  if (files && files.length > 0) {
    if (files.length === 1) return files[0]!;
    const sourceModule = sourceFileId.split('/')[0];
    for (const f of files) {
      if (f.startsWith(sourceModule ?? '')) return f;
    }
    return files[0]!;
  }

  return null;
}

export async function incrementalScan(filePaths: string[], dbPath = DEFAULT_DB_PATH): Promise<void> {
  const db = openWriteDb(dbPath);
  try {
    const classToFileMap: Record<string, string> = {};
    const shortClassToFilesMap: Record<string, string[]> = {};
    const fileToPackageMap: Record<string, string> = {};

    const allNodes = db.prepare("SELECT id, type, file_path, tags FROM nodes WHERE type = 'file'").all() as Array<{
      id: string;
      type: string;
      file_path: string;
      tags: string;
    }>;

    const allMembers = db.prepare("SELECT node_id, kind, name FROM node_members").all() as Array<{
      node_id: string;
      kind: string;
      name: string;
    }>;

    for (const member of allMembers) {
      if (member.kind === 'class') {
        if (!shortClassToFilesMap[member.name]) {
          shortClassToFilesMap[member.name] = [];
        }
        if (!shortClassToFilesMap[member.name].includes(member.node_id)) {
          shortClassToFilesMap[member.name].push(member.node_id);
        }
      }
    }

    for (const node of allNodes) {
      const filePath = join(REPO_ROOT, node.file_path);
      if (existsSync(filePath)) {
        try {
          const raw = readFileSync(filePath, 'utf-8');
          const content = stripStringsAndComments(raw);
          const pkgMatch = content.match(/package\s+([A-Za-z0-9_.]+)/);
          const packageName = pkgMatch ? pkgMatch[1] : '';
          if (packageName) {
            fileToPackageMap[node.id] = packageName;
            const declaredClasses = allMembers.filter(m => m.node_id === node.id && m.kind === 'class').map(m => m.name);
            for (const cls of declaredClasses) {
              classToFileMap[`${packageName}.${cls}`] = node.id;
            }
          }
        } catch {
          // Ignore files that fail to load
        }
      }
    }

    for (const relPath of filePaths) {
      const normalizedPath = relPath.replace(/\\/g, '/');
      const absolutePath = join(REPO_ROOT, normalizedPath);
      if (!existsSync(absolutePath)) {
        console.warn(`⚠️ File not found: ${normalizedPath}. Skipping...`);
        continue;
      }

      console.log(`⚡ [Incremental] Re-analyzing AST: ${normalizedPath}`);
      const rawContent = readFileSync(absolutePath, 'utf-8');
      const content = stripStringsAndComments(rawContent);

      const existingNode = db.prepare("SELECT summary FROM nodes WHERE id = ?").get(normalizedPath) as { summary: string } | undefined;
      
      let layer = 'Unknown';
      if (normalizedPath.startsWith('app/')) {
        layer = 'Presentation';
      } else if (normalizedPath.startsWith('wear/')) {
        layer = 'Wearable';
      } else if (normalizedPath.startsWith('shared/')) {
        layer = 'Domain-Shared';
      }

      const tags = [layer, normalizedPath.endsWith('.kt') || normalizedPath.endsWith('.kts') ? 'kotlin' : 'java'];
      const KEYWORDS_TO_TAG = [
        { tag: 'crossfade', prefix: 'crossfade' },
        { tag: 'equalizer', prefix: 'equaliz' },
        { tag: 'lyrics', prefix: 'lyric' },
        { tag: 'playlist', prefix: 'playlist' },
        { tag: 'theme', prefix: 'theme' },
        { tag: 'cast', prefix: 'cast' },
        { tag: 'sleep', prefix: 'sleep' },
        { tag: 'widget', prefix: 'widget' },
        { tag: 'replaygain', prefix: 'replaygain' }
      ];
      
      const normalizedContentForTagging = rawContent
        .replace(/([a-z])([A-Z])/g, '$1 $2')
        .replace(/([A-Z])([A-Z][a-z])/g, '$1 $2')
        .replace(/_/g, ' ');

      KEYWORDS_TO_TAG.forEach(({ tag, prefix }) => {
        const regex = new RegExp(`\\b${prefix}\\w*`, 'i');
        if (regex.test(normalizedContentForTagging)) {
          if (!tags.includes(tag)) tags.push(tag);
        }
      });

      const declaredClasses: string[] = [];
      const classMatches = content.matchAll(/(?:class|interface|object|enum\s+class)\s+([A-Za-z0-9_]+)/g);
      for (const match of classMatches) {
        if (match[1]) declaredClasses.push(match[1]);
      }

      const functions: string[] = [];
      const funMatches = content.matchAll(/fun\s+([A-Za-z0-9_]+)/g);
      for (const match of funMatches) {
        if (match[1]) functions.push(match[1]);
      }

      const summary = existingNode?.summary ?? `Core component of the ${layer} layer handling specific player business workflows.`;

      db.prepare(
        `INSERT OR REPLACE INTO nodes (id, type, name, file_path, summary, tags, layer, complexity, degree_in, degree_out)
         VALUES (?, 'file', ?, ?, ?, ?, ?, 'moderate', 0, 0)`
      ).run(
        normalizedPath,
        basename(absolutePath),
        normalizedPath,
        summary,
        tags.join(','),
        layer
      );

      db.prepare("DELETE FROM node_members WHERE node_id = ?").run(normalizedPath);
      const insertMember = db.prepare("INSERT INTO node_members (node_id, kind, name) VALUES (?, ?, ?)");
      for (const cls of declaredClasses) {
        insertMember.run(normalizedPath, 'class', cls);
      }
      for (const fn of functions) {
        insertMember.run(normalizedPath, 'function', fn);
      }

      const packageMatch = content.match(/package\s+([A-Za-z0-9_.]+)/);
      const packageName = packageMatch ? packageMatch[1] : '';
      if (packageName) {
        fileToPackageMap[normalizedPath] = packageName;
        for (const cls of declaredClasses) {
          classToFileMap[`${packageName}.${cls}`] = normalizedPath;
        }
      }

      db.prepare("DELETE FROM edges WHERE source = ? AND type IN ('imports', 'depends_on', 'calls')").run(normalizedPath);
      
      const imports = rawContent.match(/import\s+([A-Za-z0-9_.]+)/g) || [];
      const importedClassNames = new Set<string>();
      const importedPackages = new Set<string>();

      const insertEdge = db.prepare(
        `INSERT INTO edges (source, target, type, direction, weight, description) VALUES (?, ?, ?, ?, ?, ?)`
      );

      imports.forEach(imp => {
        const importedPackage = imp.replace('import ', '').trim();
        if (importedPackage.startsWith('com.theveloper.pixelplay')) {
          if (importedPackage.endsWith('.*')) {
            importedPackages.add(importedPackage.slice(0, -2));
          } else {
            const targetFileId = classToFileMap[importedPackage];
            const className = importedPackage.split('.').pop();
            if (className) importedClassNames.add(className);
            
            if (targetFileId && targetFileId !== normalizedPath) {
              insertEdge.run(normalizedPath, targetFileId, 'imports', 'forward', 0.4, null);
            }
          }
        }
      });

      const constructorRegex = /@Inject\s+(?:internal\s+|private\s+)?constructor\s*\(([^)]+)\)/g;
      let constructorMatch;
      while ((constructorMatch = constructorRegex.exec(content)) !== null) {
        const paramsText = constructorMatch[1]!;
        const paramTypes = [...paramsText.matchAll(/[A-Za-z0-9_]+/g)].map(m => m[0]);
        paramTypes.forEach(typeName => {
          if (['String', 'Int', 'Boolean', 'Long', 'Float', 'Double', 'Context', 'Application'].includes(typeName)) return;
          const targetFileId = resolveClassToFile(typeName, normalizedPath, importedClassNames, shortClassToFilesMap, classToFileMap, fileToPackageMap);
          if (targetFileId && targetFileId !== normalizedPath) {
            insertEdge.run(normalizedPath, targetFileId, 'depends_on', 'forward', 0.8, `Injects ${typeName}`);
          }
        });
      }

      for (const className of Object.keys(shortClassToFilesMap)) {
        if (declaredClasses.includes(className)) continue;
        const targetFileId = resolveClassToFile(className, normalizedPath, importedClassNames, shortClassToFilesMap, classToFileMap, fileToPackageMap);
        if (targetFileId && targetFileId !== normalizedPath) {
          const classRefRegex = new RegExp(`\\b${className}\\b`, 'g');
          if (classRefRegex.test(content)) {
            insertEdge.run(normalizedPath, targetFileId, 'calls', 'forward', 0.6, null);
          }
        }
      }
    }

    db.exec(`
      UPDATE nodes SET
        degree_in = (SELECT COUNT(*) FROM edges WHERE target = nodes.id),
        degree_out = (SELECT COUNT(*) FROM edges WHERE source = nodes.id)
      WHERE type = 'file'
    `);

    db.prepare("INSERT OR REPLACE INTO graph_sync_metadata(key, value) VALUES ('overlay_dirty_flag', '1')").run();
    console.log(`✅ Incremental scan completed successfully.`);
  } finally {
    db.close();
  }
}
