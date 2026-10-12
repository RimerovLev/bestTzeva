import type { Config } from "tailwindcss";

/**
 * Витрина свёрстана на собственных классах (src/styles/motion.css, inner.css) поверх токенов
 * src/styles/tokens.css. Tailwind остаётся для редких утилит раскладки (отступы, sr-only);
 * цвета и гарнитуры пробрасываются из токенов, чтобы утилиты не расходились с дизайн-системой.
 */
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        paper: { DEFAULT: "var(--paper)", warm: "var(--paper-warm)" },
        ink: { DEFAULT: "var(--ink)", soft: "var(--ink-soft)" },
        red: "var(--red)",
        blue: "var(--blue)",
        yellow: "var(--yellow)",
        mint: "var(--mint)",
        pink: "var(--pink)",
        violet: "var(--violet)",
      },
      fontFamily: {
        body: ["var(--f-body)"],
        display: ["var(--f-display)"],
        numeric: ["var(--f-mono)"],
      },
      boxShadow: {
        hard: "var(--sh)",
        "hard-sm": "var(--sh-sm)",
      },
    },
  },
  plugins: [],
};

export default config;
