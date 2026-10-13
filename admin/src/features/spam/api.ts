import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";

export type BlockEntry = components["schemas"]["BlockListEntry"];
export type BlockKind = "IP" | "PHONE";

export async function listBlocks() {
  return unwrap(await api.GET("/api/v1/admin/spam/block-list"));
}

/** Телефон сервер приводит к +972…; повтор уже заблокированного — 409 spam.block_list.duplicate. */
export async function addBlock(body: { kind: BlockKind; value: string; reason?: string }) {
  return unwrap(await api.POST("/api/v1/admin/spam/block-list", { body }));
}

export async function removeBlock(id: number) {
  unwrap(await api.DELETE("/api/v1/admin/spam/block-list/{id}", { params: { path: { id } } }));
}
