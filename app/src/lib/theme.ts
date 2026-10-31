import { DarkTheme, DefaultTheme, type Theme } from 'expo-router/react-navigation';

// Gleiche Werte wie in global.css; fuer Navigationselemente (Header, Hintergrund), die kein className kennen.
export const THEME = {
  light: {
    background: 'hsl(210 20% 95%)',
    foreground: 'hsl(219 46% 16%)',
    card: 'hsl(0 0% 100%)',
    primary: 'hsl(171 81% 26%)',
    mutedForeground: 'hsl(217 14% 41%)',
    destructive: 'hsl(3 71% 41%)',
    border: 'hsl(215 18% 82%)',
  },
  dark: {
    background: 'hsl(216 30% 9%)',
    foreground: 'hsl(215 25% 91%)',
    card: 'hsl(216 28% 13%)',
    primary: 'hsl(170 53% 50%)',
    mutedForeground: 'hsl(215 15% 66%)',
    destructive: 'hsl(4 100% 72%)',
    border: 'hsl(216 22% 22%)',
  },
};

export const NAV_THEME: Record<'light' | 'dark', Theme> = {
  light: {
    ...DefaultTheme,
    colors: {
      background: THEME.light.background,
      border: THEME.light.border,
      card: THEME.light.card,
      notification: THEME.light.destructive,
      primary: THEME.light.primary,
      text: THEME.light.foreground,
    },
  },
  dark: {
    ...DarkTheme,
    colors: {
      background: THEME.dark.background,
      border: THEME.dark.border,
      card: THEME.dark.card,
      notification: THEME.dark.destructive,
      primary: THEME.dark.primary,
      text: THEME.dark.foreground,
    },
  },
};
