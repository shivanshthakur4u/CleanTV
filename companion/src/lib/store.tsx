import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';
import type { Session } from '@supabase/supabase-js';
import { api, auth } from './auth';

export type Scene = 'dusk' | 'aurora' | 'sand' | 'ocean' | 'adaptive' | 'photo';
export type Action = 'controlCenter' | 'recents' | 'apps' | 'settings' | 'none';
export type Key = 'up' | 'down' | 'left' | 'right' | 'select' | 'back' | 'home' | 'controlCenter' | 'recents' | 'apps';
export type Folder = { name: string; apps: string[] };
// Appearance options arrived with TV Home 0.2; older TV builds omit them, so every field is optional.
export type Prefs = {
  glass: 'liquid' | 'frosted'; blur: number; darkMode: 'off' | 'on' | 'sunset' | 'custom'; darkStart: number; darkEnd: number;
  pill: 'minimal' | 'full' | 'weather' | 'hidden'; iconStyle: 'banner' | 'icon'; weatherCity: string; tempUnit: 'c' | 'f';
  menuAction: Action; infoAction: Action; hidden: string[]; folders: Folder[];
};
export type TVState = {
  roomName: string; scene: Scene; dim: number; clock24h: boolean; favorites: string[]; apps: { package: string; label: string }[]; model: string; sdk: number; homeVisible: boolean; wallpaperPreview?: string;
  version?: string; darkActive?: boolean; recents?: string[]; ram?: { total: number; available: number }; weather?: { temp: number; code: number; place: string };
} & Partial<Prefs>;
export type Device = { id: string; name: string; online: boolean; last_seen: string | null };
export type Settings = Partial<Pick<TVState, 'scene' | 'dim' | 'clock24h' | 'roomName' | 'favorites'> & Prefs>;
type Command = { type: 'settings'; settings: Settings } | { type: 'wallpaper'; data: string } | { type: 'launchApp'; package: string } | { type: 'navigate'; key: Key };
export const MAX_FAVORITES = 12;
const previewState: TVState = {
  roomName: 'Living room', scene: 'adaptive', dim: .2, clock24h: false, model: 'Design preview', sdk: 0, homeVisible: true, version: 'preview',
  favorites: ['preview.movies', 'preview.music', 'preview.photos'], apps: [{ package: 'preview.movies', label: 'Movies' }, { package: 'preview.music', label: 'Music' }, { package: 'preview.photos', label: 'Photos' }, { package: 'preview.games', label: 'Games' }],
  glass: 'liquid', blur: 10, darkMode: 'off', darkStart: 1140, darkEnd: 420, darkActive: false, pill: 'full', iconStyle: 'banner', weatherCity: '', tempUnit: 'c', menuAction: 'controlCenter', infoAction: 'recents', hidden: [], folders: [], recents: [],
};

function useTVStore() {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(!auth);
  const [demo, setDemo] = useState(false);
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [states, setStates] = useState<Record<string, TVState>>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const busyRef = useRef(false);
  const accountRef = useRef<string | undefined>(undefined);
  const selected = devices.find(d => d.id === selectedId) ?? null;
  const state = selectedId ? states[selectedId] ?? null : null;
  useEffect(() => {
    if (!auth) return;
    const client = auth;
    const { data } = client.auth.onAuthStateChange((_event, next) => {
      if (accountRef.current !== next?.user.id) { accountRef.current = next?.user.id; setDevices([]); setSelectedId(null); setStates({}); setMessage(''); }
      setSession(next); setReady(true);
    });
    const subscription = AppState.addEventListener('change', status => { if (status === 'active') client.auth.startAutoRefresh(); else client.auth.stopAutoRefresh(); });
    return () => { data.subscription.unsubscribe(); subscription.remove(); client.auth.stopAutoRefresh(); };
  }, []);
  const refreshDevices = useCallback(async () => {
    if (demo || !session) return;
    const account = session.user.id;
    const result = await api<{ devices: Device[] }>('/v1/devices');
    if (accountRef.current !== account) return;
    setDevices(result.devices); setSelectedId(id => result.devices.some(d => d.id === id) ? id : result.devices[0]?.id ?? null);
  }, [demo, session]);
  const refreshState = useCallback(async () => {
    if (demo || !selectedId || !session || !devices.find(d => d.id === selectedId)?.online) return;
    const account = session.user.id;
    const next = await api<TVState>(`/v1/devices/${selectedId}/state`);
    if (accountRef.current === account) { setStates(previous => ({ ...previous, [selectedId]: next })); setMessage(''); }
  }, [demo, selectedId, session, devices]);
  useEffect(() => { void refreshDevices().catch(e => setMessage(e.message)); }, [refreshDevices]);
  useEffect(() => { void refreshState().catch(e => setMessage(e.message)); }, [refreshState]);
  useEffect(() => {
    if (demo || !session) return;
    const timer = setInterval(() => { if (AppState.currentState === 'active') void refreshDevices().catch(e => setMessage(e.message)); }, 20000);
    return () => clearInterval(timer);
  }, [demo, session, refreshDevices]);
  async function perform(payload: Command) {
    if (busyRef.current) throw new Error('Wait for the current change to finish.');
    if (!selectedId || !state) throw new Error('Pair a TV and load its current settings first.');
    if (demo) {
      const next = { ...state };
      if (payload.type === 'settings') { Object.assign(next, payload.settings); if (payload.settings.darkMode) next.darkActive = payload.settings.darkMode === 'on'; }
      else if (payload.type === 'wallpaper') { next.scene = 'photo'; next.wallpaperPreview = payload.data; }
      else throw new Error('This is a design preview. Pair a TV to use remote controls.');
      setStates(previous => ({ ...previous, [selectedId]: next })); setMessage('Design preview updated. No TV is connected.'); return;
    }
    if (!selected?.online) throw new Error('TV is offline. Turn it on and check its connection.');
    const account = session?.user.id;
    busyRef.current = true; setBusy(true); setMessage('Waiting for the TV to confirm…');
    try {
      const next = await api<TVState>(`/v1/devices/${selectedId}/command`, { method: 'POST', body: JSON.stringify(payload) });
      if (accountRef.current === account) { setStates(previous => ({ ...previous, [selectedId]: next })); setDevices(previous => previous.map(d => d.id === selectedId ? { ...d, name: next.roomName } : d)); setMessage('Applied on your TV.'); }
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Change was not confirmed.'); throw error; }
    finally { busyRef.current = false; setBusy(false); }
  }
  function startPreview() { setDemo(true); setDevices([{ id: 'preview', name: 'Living room', online: false, last_seen: null }]); setSelectedId('preview'); setStates({ preview: { ...previewState } }); setMessage('Design preview · no TV connected'); }
  async function deleteAccount() {
    if (demo || !auth) throw new Error('Sign in to manage your account.');
    await api('/v1/account', { method: 'DELETE' });
    await auth.auth.signOut({ scope: 'local' }).catch(() => {});
    setSession(null); setDevices([]); setSelectedId(null); setStates({}); setMessage('');
  }
  async function signOut() {
    if (!demo && auth) { const { error } = await auth.auth.signOut({ scope: 'local' }); if (error) throw error; }
    setDemo(false); setSession(null); setDevices([]); setSelectedId(null); setStates({}); setMessage('');
  }
  return { session, ready, demo, devices, selected, selectedId, select: setSelectedId, state, busy, message, perform, refreshDevices, refreshState, startPreview, signOut, deleteAccount };
}
const Store = createContext<ReturnType<typeof useTVStore> | null>(null);
export function TVProvider({ children }: { children: ReactNode }) { return <Store.Provider value={useTVStore()}>{children}</Store.Provider>; }
export function useTV() { const store = useContext(Store); if (!store) throw new Error('TVProvider is missing.'); return store; }
