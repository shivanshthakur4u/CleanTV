import type { ComponentProps, ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Image, Pressable, ScrollView, StyleSheet, Text, View, type ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import Ionicons from '@expo/vector-icons/Ionicons';
import type { Scene, TVState } from '../lib/store';

export const colors = { bg: '#0B1212', panel: '#15201F', line: '#263431', text: '#F0F3EB', muted: '#9AAEA4', accent: '#D7EDCB', amber: '#E7C18B' };
export const scenes: { id: Exclude<Scene, 'photo'>; title: string; subtitle: string; colors: [string, string, string] }[] = [
  { id: 'adaptive', title: 'Adaptive', subtitle: 'Follows the app you focus on', colors: ['#2F6FDB', '#8EA3C6', '#C4C7CE'] },
  { id: 'dusk', title: 'Quiet dusk', subtitle: 'A softer end to the day', colors: ['#252E37', '#777E6C', '#1A3735'] },
  { id: 'aurora', title: 'Northern light', subtitle: 'A little after-dark wonder', colors: ['#0D2337', '#367C78', '#263044'] },
  { id: 'sand', title: 'Desert calm', subtitle: 'Warm, slow, and spacious', colors: ['#39303D', '#BE9273', '#65514C'] },
  { id: 'ocean', title: 'Open water', subtitle: 'Room to take a breath', colors: ['#142D49', '#488DA3', '#163947'] },
];
export function Icon({ name, size = 22, color = colors.text }: { name: ComponentProps<typeof Ionicons>['name']; size?: number; color?: ComponentProps<typeof Ionicons>['color'] }) { return <Ionicons name={name} size={size} color={color} />; }
export function Page({ children, title, eyebrow, right }: { children: ReactNode; title: string; eyebrow: string; right?: ReactNode }) {
  return <SafeAreaView edges={['top']} style={s.page}><ScrollView contentContainerStyle={s.content} keyboardShouldPersistTaps="handled"><View style={s.header}><View style={{ flex: 1 }}><Text style={s.eyebrow}>{eyebrow}</Text><Text style={s.title}>{title}</Text></View>{right}</View>{children}</ScrollView></SafeAreaView>;
}
export function Button({ title, onPress, disabled, secondary, loading, icon }: { title: string; onPress: () => void; disabled?: boolean; secondary?: boolean; loading?: boolean; icon?: ComponentProps<typeof Ionicons>['name'] }) {
  return <Pressable accessibilityRole="button" accessibilityState={{ disabled: disabled || loading }} disabled={disabled || loading} onPress={onPress} style={({ pressed }) => [s.button, secondary && s.secondary, (disabled || loading) && { opacity: .4 }, pressed && { opacity: .75 }]}>{loading ? <ActivityIndicator color={secondary ? colors.text : colors.bg} /> : <>{icon && <Icon name={icon} size={19} color={secondary ? colors.text : colors.bg} />}<Text style={[s.buttonText, secondary && { color: colors.text }]}>{title}</Text></>}</Pressable>;
}
export function Card({ children, style }: { children: ReactNode; style?: ViewStyle }) { return <View style={[s.card, style]}>{children}</View>; }
export function Section({ title, note }: { title: string; note?: string }) { return <View style={s.section}><Text style={s.sectionTitle}>{title}</Text>{note && <Text style={s.small}>{note}</Text>}</View>; }
export function Notice({ children }: { children: ReactNode }) { return <View style={s.notice}><Icon name="information-circle-outline" size={18} color={colors.muted} /><Text style={[s.small, { flex: 1 }]}>{children}</Text></View>; }
export function Atmosphere({ scene, style, children, photo, dark }: { scene: Scene; style?: ViewStyle; children?: ReactNode; photo?: string; dark?: boolean }) {
  const adaptive = scene === 'adaptive';
  const palette: [string, string, string] = adaptive && dark ? ['#2C2C2E', '#151517', '#000000'] : scenes.find(s => s.id === scene)?.colors ?? scenes[1].colors;
  return <LinearGradient colors={palette} start={adaptive ? { x: .5, y: 0 } : { x: .1, y: 0 }} end={adaptive ? { x: .5, y: 1 } : { x: .8, y: 1 }} style={[{ overflow: 'hidden' }, style]}>
    {scene === 'photo' && photo && <Image source={{ uri: `data:image/jpeg;base64,${photo}` }} style={StyleSheet.absoluteFill} resizeMode="cover" />}
    {scene !== 'photo' && !adaptive && <><View style={{ position: 'absolute', width: '150%', height: '80%', left: '-20%', bottom: '-40%', backgroundColor: '#102B2A55', borderRadius: 300, transform: [{ rotate: '9deg' }] }} /><View style={{ position: 'absolute', width: '150%', height: '75%', right: '-30%', bottom: '-48%', backgroundColor: '#081C1C77', borderRadius: 300, transform: [{ rotate: '-12deg' }] }} /></>}{children}
  </LinearGradient>;
}
/** A row of mutually exclusive options. */
export function Choice<T extends string | number>({ options, value, onChange, disabled }: { options: { value: T; label: string }[]; value: T | undefined; onChange: (value: T) => void; disabled?: boolean }) {
  return <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>{options.map(option => {
    const selected = option.value === value;
    return <Pressable key={String(option.value)} accessibilityRole="button" accessibilityState={{ selected, disabled }} disabled={disabled} onPress={() => onChange(option.value)} style={({ pressed }) => [{ paddingVertical: 10, paddingHorizontal: 14, borderRadius: 12, borderWidth: 1, borderColor: selected ? colors.accent : colors.line, backgroundColor: selected ? colors.accent : colors.panel, opacity: disabled ? .4 : pressed ? .7 : 1 }]}><Text style={{ color: selected ? colors.bg : colors.text, fontSize: 13, fontWeight: selected ? '600' : '400' }}>{option.label}</Text></Pressable>;
  })}</View>;
}
export function Field({ label, children }: { label: string; children: ReactNode }) { return <View style={{ gap: 10, marginBottom: 18 }}><Text style={s.small}>{label}</Text>{children}</View>; }
export const clockLabel = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
export function TVPreview({ state }: { state: TVState }) {
  const [clock, setClock] = useState(() => new Date());
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const tick = () => { setClock(new Date()); timer = setTimeout(tick, 60000 - Date.now() % 60000); };
    timer = setTimeout(tick, 60000 - Date.now() % 60000);
    return () => clearTimeout(timer);
  }, []);
  const apps = state.favorites.map(pkg => state.apps.find(a => a.package === pkg)).filter(a => a !== undefined);
  const dark = !!state.darkActive; const glass = state.glass === 'frosted' ? (dark ? '#2C2C2ECC' : '#EDEDF2B3') : (dark ? '#FFFFFF22' : '#FFFFFF55');
  const tile = dark ? '#2C2C2E' : '#F5F5F7'; const ink = dark ? '#FFFFFF' : '#1C1C1E';
  if (state.glass) return <View style={s.tvFrame}><Atmosphere scene={state.scene} dark={dark} photo={state.wallpaperPreview} style={{ aspectRatio: 16 / 9 }}><View style={[StyleSheet.absoluteFill, { backgroundColor: '#000', opacity: state.scene === 'adaptive' ? 0 : state.dim + (dark ? .3 : 0) }]} /><View style={{ padding: 14, flex: 1, justifyContent: 'space-between' }}>
    <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}><View><Text style={{ color: '#FFF', fontSize: 22, fontWeight: '300' }}>{clock.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: !state.clock24h })}</Text><Text style={{ color: '#FFFFFFDD', fontSize: 7, marginTop: 3 }}>{state.roomName}{state.weather ? ` · ${state.weather.temp}° ${state.weather.place}` : ''}</Text></View>{state.pill !== 'hidden' && <View style={{ backgroundColor: '#FFFFFF33', borderColor: '#FFFFFF66', borderWidth: StyleSheet.hairlineWidth, borderRadius: 99, paddingHorizontal: 7, paddingVertical: 3, alignSelf: 'flex-start' }}><Text style={{ color: '#FFF', fontSize: 7 }}>{state.pill === 'weather' ? `${state.weather?.temp ?? '--'}°` : clock.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit', hour12: !state.clock24h })}</Text></View>}</View>
    <View style={{ backgroundColor: glass, borderColor: '#FFFFFF44', borderWidth: StyleSheet.hairlineWidth, borderRadius: 9, padding: 5, flexDirection: 'row', gap: 4 }}>{apps.length ? apps.slice(0, 6).map(a => <View key={a.package} style={{ flex: 1, aspectRatio: 16 / 9, backgroundColor: tile, borderRadius: 4, alignItems: 'center', justifyContent: 'center', padding: 2 }}><Text numberOfLines={1} style={{ color: ink, fontSize: 6 }}>{a.label}</Text></View>) : <Text style={{ fontSize: 9, color: ink, padding: 6 }}>Choose your favourite apps</Text>}</View>
  </View></Atmosphere></View>;
  return <View style={s.tvFrame}><Atmosphere scene={state.scene} photo={state.wallpaperPreview} style={{ aspectRatio: 16 / 9 }}><View style={[StyleSheet.absoluteFill, { backgroundColor: '#000', opacity: state.dim }]} /><View style={{ padding: 18, flex: 1, justifyContent: 'space-between' }}><View><Text style={{ color: '#FFF', fontSize: 28, fontWeight: '300' }}>{clock.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: !state.clock24h })}</Text><Text style={{ color: '#E5EBDF', fontSize: 8, marginTop: 3 }}>{state.roomName}</Text></View><View style={{ flexDirection: 'row', gap: 5 }}>{apps.length ? apps.slice(0, 5).map(a => <View key={a.package} style={{ flex: 1, backgroundColor: '#142220B8', borderColor: '#FFFFFF22', borderWidth: 1, borderRadius: 7, minHeight: 31, alignItems: 'center', justifyContent: 'center', padding: 4 }}><Text numberOfLines={1} style={{ color: '#FFF', fontSize: 8 }}>{a.label}</Text></View>) : <Text style={{ fontSize: 10, color: '#DFE8DC' }}>Choose your favorite apps</Text>}</View></View></Atmosphere></View>;
}
export function report(error: unknown) { Alert.alert('Could not complete this change', error instanceof Error ? error.message : 'Please try again.'); }
export const s = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.bg }, content: { padding: 24, paddingBottom: 42 }, header: { flexDirection: 'row', alignItems: 'center', gap: 14, marginBottom: 27 },
  eyebrow: { color: colors.muted, fontSize: 10, letterSpacing: 2.3, fontWeight: '600', marginBottom: 10 }, title: { color: colors.text, fontSize: 34, letterSpacing: -1.2, fontWeight: '400' }, text: { color: colors.text, fontSize: 15, lineHeight: 23 }, small: { color: colors.muted, fontSize: 12, lineHeight: 19 },
  card: { backgroundColor: colors.panel, borderColor: colors.line, borderWidth: 1, borderRadius: 20, padding: 19, gap: 10 }, button: { backgroundColor: colors.accent, borderRadius: 15, paddingVertical: 16, paddingHorizontal: 18, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9, minHeight: 52 }, buttonText: { color: colors.bg, fontSize: 14, fontWeight: '600' }, secondary: { backgroundColor: colors.panel, borderColor: colors.line, borderWidth: 1 },
  section: { marginTop: 29, marginBottom: 14, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, sectionTitle: { color: colors.text, fontSize: 17, fontWeight: '500', letterSpacing: -.3 }, notice: { flexDirection: 'row', alignItems: 'flex-start', gap: 9, paddingVertical: 16 },
  input: { borderColor: colors.line, borderWidth: 1, borderRadius: 14, color: colors.text, padding: 16, minHeight: 54, backgroundColor: colors.panel, fontSize: 16 }, row: { flexDirection: 'row', alignItems: 'center', gap: 12 }, tvFrame: { backgroundColor: '#020505', borderColor: '#34433D', borderWidth: 4, borderRadius: 17, padding: 2, overflow: 'hidden' },
});
