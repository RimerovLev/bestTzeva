import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";

const ANCHORS: { key: keyof Dictionary["nav"]; anchor: string }[] = [
  { key: "works", anchor: "works" },
  { key: "prices", anchor: "catalog" },
  { key: "colors", anchor: "colors" },
  { key: "services", anchor: "services" },
  { key: "about", anchor: "about" },
];

/**
 * Подвал: огромное название, буквы которого подпрыгивают и красятся при наведении (чистый CSS,
 * цвет по кругу палитры — без JS), меню и реквизиты. Реквизиты — плейсхолдеры до данных клиента.
 * Буквы режутся по символам: иврит не соединяет буквы, поэтому разбивка его не портит.
 */
export function Footer({ locale, dict }: { locale: Locale; dict: Dictionary }) {
  const year = new Date().getFullYear();
  const name = dict.meta.siteName;

  return (
    <footer className="mega-foot">
      <div className="wrap">
        <p className="mega" aria-label={name}>
          {[...name].map((ch, i) =>
            ch === " " ? <span key={i} className="sp" aria-hidden>&nbsp;</span> : <span key={i} aria-hidden>{ch}</span>,
          )}
        </p>
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
