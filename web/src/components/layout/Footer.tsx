import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { LogoRoller } from "@/components/motion/LogoRoller";

const ANCHORS: { key: keyof Dictionary["nav"]; anchor: string }[] = [
  { key: "works", anchor: "works" },
  { key: "prices", anchor: "catalog" },
  { key: "colors", anchor: "colors" },
  { key: "services", anchor: "services" },
  { key: "about", anchor: "about" },
];

/**
 * Подвал: крупный логотип на бумажной табличке (на чёрном фоне синее «צבע» иначе теряется) —
 * при прокрутке его «прокатывает» валик (LogoRoller), меню и реквизиты. Реквизиты — плейсхолдеры до данных клиента.
 */
export function Footer({ locale, dict }: { locale: Locale; dict: Dictionary }) {
  const year = new Date().getFullYear();
  const name = dict.meta.siteName;

  return (
    <footer className="mega-foot">
      <div className="wrap">
        <Link href={`/${locale}`} className="mega-logo" aria-label={name}>
          <LogoRoller />
        </Link>
        <div className="foot">
          <nav aria-label={dict.footer.navLabel}>
            {ANCHORS.map(({ key, anchor }) => (
              <Link key={anchor} href={`/${locale}#${anchor}`}>{dict.nav[key]}</Link>
            ))}
          </nav>
          <dl>
            <div><dt>{dict.footer.companyNumber}</dt><dd><bdi>00-0000000</bdi></dd></div>
            <div><dt>{dict.footer.address}</dt><dd>—</dd></div>
            <div><dt>{dict.footer.hours}</dt><dd>{dict.home.contactHoursValue}</dd></div>
          </dl>
          <small>
            © <bdi>{year}</bdi> {dict.meta.companyLegal} · {dict.meta.tagline}. {dict.footer.rights}
          </small>
        </div>
      </div>
    </footer>
  );
}
