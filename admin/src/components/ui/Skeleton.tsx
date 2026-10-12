/** Скелетон загрузки: прямоугольник-плейсхолдер с пульсацией. Размер задаётся className. */
export function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse rounded-control bg-line/60 ${className}`} aria-hidden />;
}

/** Полноэкранный скелетон приложения — пока идёт refresh по куке при старте вкладки. */
export function AppSkeleton() {
  return (
    <div className="flex h-full">
      <div className="hidden w-64 shrink-0 bg-navy-deep md:block" />
      <div className="flex-1 space-y-4 p-8">
        <Skeleton className="h-8 w-64" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
        <Skeleton className="h-64" />
      </div>
    </div>
  );
}
