import { useEffect, useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ChevronUp, ChevronDown, GripVertical } from "lucide-react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { resolveErrorMessage } from "@/lib/api/messages";
import { listCategories, type CategoryResponse } from "./api";
import { reorderCategories, deleteCategory, createCategory, updateCategory, type CategorySaveRequest } from "./refApi";

const MAX_DEPTH = 3;

interface Node {
  id: number;
  name: string;
  slug: string;
  active: boolean;
  products: number;
  children: Node[];
}

/** Плоский список из ответа /categories (там parent_id) → дерево. */
function buildTree(list: CategoryResponse[]): Node[] {
  const byId = new Map<number, Node>();
  for (const c of list) if (c.id != null) byId.set(c.id, { id: c.id, name: c.name ?? c.slug ?? "", slug: c.slug ?? "", active: c.active ?? true, products: c.product_count ?? 0, children: [] });
  const roots: Node[] = [];
  for (const c of list) {
    if (c.id == null) continue;
    const node = byId.get(c.id)!;
    const parent = c.parent_id != null ? byId.get(c.parent_id) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }
  return roots;
}

function height(n: Node): number {
  return n.children.length === 0 ? 1 : 1 + Math.max(...n.children.map(height));
}

function findAndRemove(nodes: Node[], id: number): Node | null {
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i]!.id === id) return nodes.splice(i, 1)[0]!;
    const found = findAndRemove(nodes[i]!.children, id);
    if (found) return found;
  }
  return null;
}
function findNode(nodes: Node[], id: number): Node | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    const f = findNode(n.children, id);
    if (f) return f;
  }
  return null;
}
function contains(parent: Node, id: number): boolean {
  return parent.id === id || parent.children.some((c) => contains(c, id));
}

export function CategoriesPage() {
  const { dict, locale } = useI18n();
  const t = dict.categories;
  const qc = useQueryClient();
  const categoriesQuery = useQuery({ queryKey: ["categories", locale], queryFn: listCategories });

  const [tree, setTree] = useState<Node[]>([]);
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ mode: "new" | "edit"; node?: Node } | null>(null);
  const [toDelete, setToDelete] = useState<Node | null>(null);

  useEffect(() => {
    if (categoriesQuery.data) {
      setTree(buildTree(categoriesQuery.data));
      setDirty(false);
    }
  }, [categoriesQuery.data]);

  const reorderMutation = useMutation({
    mutationFn: (items: { id: number; parent_id?: number; sort_order: number }[]) => reorderCategories({ items }),
    onSuccess: () => { setDirty(false); qc.invalidateQueries({ queryKey: ["categories"] }); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteCategory(id),
    onSuccess: () => { setToDelete(null); qc.invalidateQueries({ queryKey: ["categories"] }); },
  });

  /** Переместить node в новый parent (или в корень). Проверка глубины ≤ 3. */
  function move(dragId: number, parentId: number | null) {
    setError(null);
    setTree((prev) => {
      const next = structuredClone(prev) as Node[];
      const target = parentId != null ? findNode(next, parentId) : null;
      if (parentId != null) {
        const dragged = findNode(next, dragId);
        if (!dragged || !target || contains(dragged, parentId)) return prev; // нельзя в самого себя/потомка
        const parentDepth = depthOf(next, parentId) + 1; // уровень будущего родителя (1-based)
        if (parentDepth + height(dragged) - 1 > MAX_DEPTH) { setError(t.depthExceeded); return prev; }
      }
      const node = findAndRemove(next, dragId);
      if (!node) return prev;
      if (target) target.children.push(node);
      else next.push(node);
      setDirty(true);
      return next;
    });
  }

  function reorderSibling(id: number, dir: -1 | 1) {
    setTree((prev) => {
      const next = structuredClone(prev) as Node[];
      const siblings = siblingListOf(next, id);
      const i = siblings.findIndex((n) => n.id === id);
      const j = i + dir;
      if (i < 0 || j < 0 || j >= siblings.length) return prev;
      [siblings[i], siblings[j]] = [siblings[j]!, siblings[i]!];
      setDirty(true);
      return next;
    });
  }

  const items = useMemo(() => serialize(tree), [tree]);

  return (
    <section>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-h1 font-bold text-navy">{t.title}</h1>
        <div className="flex gap-2">
          {dirty && <span className="self-center text-sm text-warn-fg">{t.orderChanged}</span>}
          <Button variant="outline" disabled={!dirty || reorderMutation.isPending} onClick={() => reorderMutation.mutate(items)}>{t.saveOrder}</Button>
          <Button variant="primary" onClick={() => setEditing({ mode: "new" })}>{t.new}</Button>
        </div>
      </div>
      <p className="mb-3 text-sm text-ink-faint">{t.dragHint}</p>
      {error && <p className="mb-3 text-sm text-crit-fg">{error}</p>}

      <div className="rounded-card border border-line bg-surface p-2 shadow-card" onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); const id = Number(e.dataTransfer.getData("text")); if (id) move(id, null); }}>
        {tree.length === 0 ? (
          <p className="p-8 text-center text-ink-faint">{t.empty}</p>
        ) : (
          <ul>{tree.map((n) => <Row key={n.id} node={n} depth={0} t={t} onMove={move} onReorder={reorderSibling} onEdit={(node) => setEditing({ mode: "edit", node })} onDelete={setToDelete} />)}</ul>
        )}
      </div>

      {editing && (
        <CategoryForm
          mode={editing.mode}
          node={editing.node}
          categories={categoriesQuery.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); qc.invalidateQueries({ queryKey: ["categories"] }); }}
        />
      )}

      <Modal open={toDelete != null} onClose={() => setToDelete(null)} title={t.deleteTitle}
        footer={<><Button variant="muted" onClick={() => setToDelete(null)}>{t.cancel}</Button><Button variant="primary" disabled={deleteMutation.isPending} onClick={() => toDelete && deleteMutation.mutate(toDelete.id)}>{t.delete}</Button></>}>
        <p>{t.deleteBody.replace("{products}", String(toDelete?.products ?? 0)).replace("{children}", String(toDelete?.children.length ?? 0))}</p>
        {deleteMutation.isError && <p className="mt-2 text-sm text-crit-fg">{resolveErrorMessage(dict.errors, deleteMutation.error).text}</p>}
      </Modal>
    </section>
  );
}

function Row({ node, depth, t, onMove, onReorder, onEdit, onDelete }: {
  node: Node; depth: number; t: Record<string, string>;
  onMove: (id: number, parent: number | null) => void;
  onReorder: (id: number, dir: -1 | 1) => void;
  onEdit: (n: Node) => void; onDelete: (n: Node) => void;
}) {
  return (
    <li>
      <div
        draggable
        onDragStart={(e) => { e.dataTransfer.setData("text", String(node.id)); e.stopPropagation(); }}
        onDragOver={(e) => { e.preventDefault(); e.stopPropagation(); }}
        onDrop={(e) => { e.preventDefault(); e.stopPropagation(); const id = Number(e.dataTransfer.getData("text")); if (id && id !== node.id) onMove(id, node.id); }}
        className="group flex items-center gap-2 rounded-control px-2 py-2 hover:bg-paper-warm"
        style={{ marginInlineStart: depth * 24 }}
      >
        <GripVertical size={16} className="cursor-grab text-ink-faint" aria-hidden />
        <span className={`flex-1 text-sm ${node.active ? "text-navy" : "text-ink-faint line-through"}`}>{node.name}</span>
        <span className="numeric text-xs text-ink-faint">{node.products} {t.products}</span>
        <button type="button" onClick={() => onReorder(node.id, -1)} aria-label="up" className="text-ink-faint hover:text-navy"><ChevronUp size={16} /></button>
        <button type="button" onClick={() => onReorder(node.id, 1)} aria-label="down" className="text-ink-faint hover:text-navy"><ChevronDown size={16} /></button>
        <button type="button" onClick={() => onEdit(node)} className="text-xs text-brass hover:underline">{t.edit}</button>
        <button type="button" onClick={() => onDelete(node)} className="text-xs text-crit-fg hover:underline">{t.delete}</button>
      </div>
      {node.children.length > 0 && <ul>{node.children.map((c) => <Row key={c.id} node={c} depth={depth + 1} t={t} onMove={onMove} onReorder={onReorder} onEdit={onEdit} onDelete={onDelete} />)}</ul>}
    </li>
  );
}

// ── tree helpers ─────────────────────────────────────────────────────────────
function siblingListOf(nodes: Node[], id: number): Node[] {
  if (nodes.some((n) => n.id === id)) return nodes;
  for (const n of nodes) {
    const r = siblingListOf(n.children, id);
    if (r.length) return r;
  }
  return [];
}
function depthOf(nodes: Node[], id: number, d = 0): number {
  for (const n of nodes) {
    if (n.id === id) return d;
    const r = depthOf(n.children, id, d + 1);
    if (r >= 0) return r;
  }
  return -1;
}
function serialize(tree: Node[]): { id: number; parent_id?: number; sort_order: number }[] {
  const out: { id: number; parent_id?: number; sort_order: number }[] = [];
  const walk = (nodes: Node[], parent?: number) => nodes.forEach((n, i) => { out.push({ id: n.id, parent_id: parent, sort_order: i }); walk(n.children, n.id); });
  walk(tree);
  return out;
}

// ── форма категории ──────────────────────────────────────────────────────────
function CategoryForm({ mode, node, categories, onClose, onSaved }: {
  mode: "new" | "edit"; node?: Node; categories: CategoryResponse[];
  onClose: () => void; onSaved: () => void;
}) {
  const { dict } = useI18n();
  const t = dict.categories;
  const [name, setName] = useState(node?.name ?? "");
  const [slug, setSlug] = useState(node?.slug ?? "");
  const [parentId, setParentId] = useState<string>("");
  const [active, setActive] = useState(node?.active ?? true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true); setError(null);
    const body: CategorySaveRequest = {
      slug: slug.trim() || undefined,
      parent_id: parentId ? Number(parentId) : undefined,
      active,
      translations: { he: { name: name.trim() } },
    };
    try {
      if (mode === "edit" && node) await updateCategory(node.id, body);
      else await createCategory(body);
      onSaved();
    } catch (e) {
      setError(resolveErrorMessage(dict.errors, e).text);
    } finally { setBusy(false); }
  }

  return (
    <Modal open onClose={onClose} title={mode === "new" ? t.formNew : t.formEdit}
      footer={<><Button variant="muted" onClick={onClose}>{t.cancel}</Button><Button variant="primary" disabled={busy || !name.trim()} onClick={save}>{t.save}</Button></>}>
      <div className="space-y-3">
        <label className="flex flex-col gap-1 text-sm"><span className="text-ink-soft">{t.name}</span><input value={name} onChange={(e) => setName(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        <label className="flex flex-col gap-1 text-sm"><span className="text-ink-soft">{t.slug}</span><input value={slug} onChange={(e) => setSlug(e.target.value)} placeholder={t.slugAuto} className="rounded-control border border-line-strong px-3 py-2" /></label>
        <label className="flex flex-col gap-1 text-sm"><span className="text-ink-soft">{t.parent}</span>
          <select value={parentId} onChange={(e) => setParentId(e.target.value)} className="rounded-control border border-line-strong px-2 py-2">
            <option value="">{t.root}</option>
            {categories.filter((c) => c.id !== node?.id).map((c) => <option key={c.id} value={c.id}>{c.name ?? c.slug}</option>)}
          </select>
        </label>
        <label className="flex items-center gap-2 text-sm text-ink-soft"><input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="accent-brass" />{t.active}</label>
        {error && <p className="text-sm text-crit-fg">{error}</p>}
      </div>
    </Modal>
  );
}
