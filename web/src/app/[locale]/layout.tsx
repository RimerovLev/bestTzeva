import type { ReactNode } from "react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { locales, dir, isLocale, type Locale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { fontVariables } from "@/lib/fonts";
import { SITE_URL } from "@/lib/seo";
import { Header } from "@/components/layout/Header";
import { Footer } from "@/components/layout/Footer";
import { MotionProvider } from "@/components/motion/MotionProvider";
import { Providers } from "@/app/providers";

export function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  return {
    metadataBase: new URL(SITE_URL),
    title: {
      default: `${dict.meta.siteName} — ${dict.meta.tagline}`,
      template: `%s · ${dict.meta.siteName}`,
    },
    description: dict.home.hero.body,
    applicationName: dict.meta.siteName,
    formatDetection: { telephone: false },
  };
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const typedLocale: Locale = locale;
  const dict = await getDictionary(typedLocale);

  return (
    <html lang={typedLocale} dir={dir[typedLocale]} className={`${fontVariables} scroll-smooth`}>
      <body className="flex min-h-screen flex-col overflow-x-clip text-ink">
        <Providers>
          <MotionProvider>
            <Header locale={typedLocale} dict={dict} />
            <main id="main" className="relative flex-1">
              {children}
            </main>
            <Footer locale={typedLocale} dict={dict} />
          </MotionProvider>
        </Providers>
      </body>
    </html>
  );
}
