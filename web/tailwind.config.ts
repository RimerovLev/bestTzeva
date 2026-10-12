import type { Config } from "tailwindcss";

/**
 * Все значения ссылаются на CSS-переменные из src/styles/tokens.css — единый источник
 * правды по дизайн-системе (docs/design-system.md). Цвета, радиусы и отступы значениями
 * мимо токенов в компонентах не пишутся.
 */
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        red: "var(--red)",
        blue: "var(--blue)",
        yellow: "var(--yellow)",
        mint: "var(--mint)",
        pink: "var(--pink)",
        violet: "var(--violet)",
        // алиасы первой редакции — см. tokens.css
        navy: { DEFAULT: "var(--navy)", deep: "var(--navy-deep)" },
        brass: { DEFAULT: "var(--brass)", light: "var(--brass-light)" },
        terracotta: "var(--terracotta)",
        sage: { DEFAULT: "var(--sage)", pale: "var(--sage-pale)" },
        paper: { DEFAULT: "var(--paper)", warm: "var(--paper-warm)" },
        surface: "var(--surface)",
        ink: { DEFAULT: "var(--ink)", soft: "var(--ink-soft)", faint: "var(--ink-faint)" },
        line: { DEFAULT: "var(--line)", strong: "var(--line-strong)" },
        // семантика статусов заявки
        "ok-fg": "var(--ok-fg)", "ok-bg": "var(--ok-bg)",
        "info-fg": "var(--info-fg)", "info-bg": "var(--info-bg)",
        "warn-fg": "var(--warn-fg)", "warn-bg": "var(--warn-bg)",
        "crit-fg": "var(--crit-fg)", "crit-bg": "var(--crit-bg)",
      },
      fontFamily: {
        // одна пара стеков на оба языка: латиница/кириллица, затем иврит (src/lib/fonts.ts)
        heebo: ["var(--f-body)"],
        manrope: ["var(--f-body)"],
        body: ["var(--f-body)"],
        display: ["var(--f-display)"],
        numeric: ["var(--f-mono)"],
      },
      fontSize: {
        display: ["var(--fs-display)", { lineHeight: "1.1" }],
        h1: ["var(--fs-h1)", { lineHeight: "1.2" }],
        h2: ["var(--fs-h2)", { lineHeight: "1.3" }],
        h3: ["var(--fs-h3)", { lineHeight: "1.4" }],
        body: ["var(--fs-body)", { lineHeight: "1.6" }],
        sm: ["var(--fs-sm)", { lineHeight: "1.5" }],
        xs: ["var(--fs-xs)", { lineHeight: "1.4" }],
      },
      borderRadius: {
        control: "var(--radius-control)",
        card: "var(--radius-card)",
        pill: "var(--radius-pill)",
      },
      boxShadow: {
        card: "var(--shadow-card)",
        lift: "var(--sh)",
        hard: "var(--sh)",
        "hard-sm": "var(--sh-sm)",
      },
      spacing: {
        1: "var(--sp-1)", 2: "var(--sp-2)", 3: "var(--sp-3)", 4: "var(--sp-4)",
        6: "var(--sp-6)", 8: "var(--sp-8)", 12: "var(--sp-12)", 16: "var(--sp-16)",
      },
    },
  },
  plugins: [],
};

export default config;
