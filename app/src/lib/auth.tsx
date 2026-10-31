import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, ReactNode, useContext, useEffect, useState } from 'react';

import { TokenResponse } from './api';

const KEY = 'rechnio.session';

interface AuthState {
  session: TokenResponse | null;
  loading: boolean;
  signIn: (s: TokenResponse) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<TokenResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    AsyncStorage.getItem(KEY)
      .then((raw) => setSession(raw ? JSON.parse(raw) : null))
      .catch(() => setSession(null))
      .finally(() => setLoading(false));
  }, []);

  const signIn = async (s: TokenResponse) => {
    setSession(s);
    await AsyncStorage.setItem(KEY, JSON.stringify(s));
  };

  const signOut = async () => {
    setSession(null);
    await AsyncStorage.removeItem(KEY);
  };

  return <AuthContext.Provider value={{ session, loading, signIn, signOut }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth muss innerhalb von AuthProvider verwendet werden');
  return ctx;
}

/** Token des angemeldeten Benutzers; Bildschirme hinter dem Login rufen nur das auf. */
export function useToken(): string {
  const { session } = useAuth();
  return session?.token ?? '';
}
