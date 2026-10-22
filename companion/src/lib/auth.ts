import { createClient, processLock } from '@supabase/supabase-js';
import * as SecureStore from 'expo-secure-store';

const url = process.env.EXPO_PUBLIC_SUPABASE_URL;
const key = process.env.EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
export const relayUrl = process.env.EXPO_PUBLIC_RELAY_URL?.trim().replace(/\/$/, '') ?? '';
export const configured = !!(url?.startsWith('https://') && key && relayUrl && (relayUrl.startsWith('https://') || __DEV__ && relayUrl.startsWith('http://')));

function manifest(raw: string | null): { generation: string; count: number } | null {
  try {
    const value = JSON.parse(raw ?? 'null');
    return value && typeof value.generation === 'string' && /^[a-z0-9]+$/.test(value.generation) && Number.isInteger(value.count) && value.count > 0 && value.count <= 40 ? value : null;
  } catch { return null; }
}

// Switch the keychain manifest only after every session chunk has been written.
const storage = {
  async getItem(name: string) {
    const stored = manifest(await SecureStore.getItemAsync(name));
    if (!stored) return null;
    const { generation, count } = stored;
    const parts = await Promise.all(Array.from({ length: count }, (_, i) => SecureStore.getItemAsync(`${name}.${generation}.${i}`)));
    return parts.some(part => part === null) ? null : parts.join('');
  },
  async setItem(name: string, value: string) {
    const previous = await SecureStore.getItemAsync(name);
    const generation = Date.now().toString(36) + Math.random().toString(36).slice(2);
    const count = Math.ceil(value.length / 1500);
    if (count > 40) throw new Error('Account session is too large to save securely.');
    await Promise.all(Array.from({ length: count }, (_, i) => SecureStore.setItemAsync(`${name}.${generation}.${i}`, value.slice(i * 1500, (i + 1) * 1500))));
    await SecureStore.setItemAsync(name, JSON.stringify({ generation, count }));
    const old = manifest(previous);
    if (old) {
      await Promise.all(Array.from({ length: Math.min(old.count, 40) }, (_, i) => SecureStore.deleteItemAsync(`${name}.${old.generation}.${i}`).catch(() => {})));
    }
  },
  async removeItem(name: string) {
    const previous = await SecureStore.getItemAsync(name);
    await SecureStore.deleteItemAsync(name);
    const old = manifest(previous);
    if (old) {
      await Promise.all(Array.from({ length: Math.min(old.count, 40) }, (_, i) => SecureStore.deleteItemAsync(`${name}.${old.generation}.${i}`)));
    }
  },
};
export const auth = configured ? createClient(url!, key!, {
  auth: { storage, persistSession: true, autoRefreshToken: true, detectSessionInUrl: false, lock: processLock },
}) : null;

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  if (!auth) throw new Error('Configure the account and relay services first.');
  const { data } = await auth.auth.getSession();
  if (!data.session) throw new Error('Sign in to continue.');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 22000);
  try {
    const response = await fetch(relayUrl + path, { ...init, signal: controller.signal, headers: { ...(init?.body ? { 'Content-Type': 'application/json' } : {}), Authorization: `Bearer ${data.session.access_token}`, ...init?.headers } });
    const body = await response.json();
    if (!response.ok) throw new Error(body.error || 'Could not complete the request.');
    return body as T;
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw new Error('Connection timed out. Check the TV status before retrying.');
    throw error;
  } finally { clearTimeout(timer); }
}
