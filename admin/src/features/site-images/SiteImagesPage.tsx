import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { resolveErrorMessage } from "@/lib/api/messages";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  SITE_IMAGE_SLOTS,
  listSiteImages,
  resetSiteImage,
  uploadSiteImage,
  type SiteImageView,
} from "./api";

const ACCEPT = "image/jpeg,image/png,image/webp";

/**
 * Картинки сайта (только ADMIN): по слотам (банка, до/после, баннер, лого) овнер загружает свою
 * картинку взамен дефолтной. Пустой слот — витрина показывает картинку из сборки.
 */
export function SiteImagesPage() {
  const { user } = useAuth();
  const { dict } = useI18n();
  const t = dict.siteImages;
  const qc = useQueryClient();

  const allowed = !!user && canAccessPath(user.role, "/site-images");
  const q = useQuery({ queryKey: ["admin-site-images"], queryFn: listSiteImages, enabled: allowed });
  const [error, setError] = useState<string | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: ["admin-site-images"] });

  const uploadM = useMutation({
    mutationFn: ({ slot, file }: { slot: string; file: File }) => uploadSiteImage(slot, file),
    onSuccess: () => { setError(null); invalidate(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });
  const resetM = useMutation({
    mutationFn: (slot: string) => resetSiteImage(slot),
    onSuccess: () => { setError(null); invalidate(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });

  const bySlot = new Map<string, SiteImageView>((q.data ?? []).map((v) => [v.slot, v]));
  const busySlot = uploadM.isPending ? uploadM.variables?.slot : resetM.isPending ? resetM.variables : undefined;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-xl font-semibold">{t.title}</h1>
        <p className="mt-1 text-sm text-muted">{t.intro}</p>
      </header>

      {error && <p className="rounded-control bg-terracotta/10 px-3 py-2 text-sm text-terracotta">{error}</p>}

      {q.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
        </div>
      ) : q.isError ? (
        <p className="text-sm text-terracotta">{t.loadError}</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {SITE_IMAGE_SLOTS.map((slot) => (
            <SlotCard
              key={slot}
              label={t.slots[slot]}
              view={bySlot.get(slot)}
              t={t}
              busy={busySlot === slot}
              onUpload={(file) => uploadM.mutate({ slot, file })}
              onReset={() => resetM.mutate(slot)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function SlotCard({
  label,
  view,
  t,
  busy,
  onUpload,
  onReset,
}: {
  label: string;
  view: SiteImageView | undefined;
  t: typeof import("@/dictionaries/he.json")["siteImages"];
  busy: boolean;
  onUpload: (file: File) => void;
  onReset: () => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const url = view?.url ?? null;

  return (
    <div className="rounded-card border border-line bg-surface p-4">
      <div className="flex items-center justify-between">
        <h2 className="font-medium">{label}</h2>
        <span className="text-xs text-muted">{url ? t.current : t.default}</span>
      </div>

      <div className="mt-3 flex h-40 items-center justify-center overflow-hidden rounded-control bg-paper">
        {url ? (
          <img src={url} alt={label} className="max-h-40 max-w-full object-contain" />
        ) : (
          <span className="text-xs text-muted">{t.default}</span>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onUpload(file);
          e.target.value = "";
        }}
      />

      <div className="mt-3 flex items-center gap-2">
        <Button variant="primary" disabled={busy} onClick={() => inputRef.current?.click()}>
          {busy ? t.uploading : url ? t.replace : t.upload}
        </Button>
        {url && (
          <Button variant="outline" disabled={busy} onClick={onReset}>
            {t.reset}
          </Button>
        )}
      </div>
      <p className="mt-2 text-xs text-muted">{t.hint}</p>
    </div>
  );
}
