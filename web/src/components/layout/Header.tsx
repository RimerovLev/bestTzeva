import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { NavBar, type NavItem } from "./NavBar";

/** Разделы главной. Вкладки — якоря к секциям одностраничника, с других страниц ведут на главную. */
const NAV: { key: keyof Dictionary["nav"]; anchor: string }[] = [
  { key: "works", anchor: "works" },
  { key: "prices", anchor: "catalog" },
  { key: "colors", anchor: "colors" },
  { key: "services", anchor: "services" },
  { key: "about", anchor: "about" },
];

/**
 * Шапка: логотип в белой плашке, меню-якоря, заявка со счётчиком, язык и «Связаться».
 * Прозрачна над героем, на прокрутке получает бумажный фон и чернильную линию.
 */
export function Header({ locale, dict }: { locale: Locale; dict: Dictionary }) {
  const items: NavItem[] = NAV.map(({ key, anchor }) => ({ anchor, href: `/${locale}#${anchor}`, label: dict.nav[key] }));

  return (
    <NavBar
      locale={locale}
      items={items}
      labels={{
        home: dict.header.home,
        sections: dict.header.sections,
        menu: dict.header.menu,
        request: dict.header.request,
        contact: dict.header.contact,
        languageMenu: dict.header.languageMenu,
      }}
    />
  );
}
