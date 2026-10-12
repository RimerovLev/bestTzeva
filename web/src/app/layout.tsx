import type { ReactNode } from "react";
import "@/styles/globals.css";

/**
 * Корневой layout — сквозной. Тег <html> с dir и lang рендерит layout локали
 * (app/[locale]/layout.tsx), потому что направление и язык известны только там.
 */
export default function RootLayout({ children }: { children: ReactNode }) {
  return children;
}
