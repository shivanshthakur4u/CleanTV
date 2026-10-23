import { Pressable, Text, View } from 'react-native';
import { useTV, type Key } from '../../lib/store';
import { Card, Icon, Notice, Page, Section, colors, report, s } from '../../components/ui';

export default function Remote() {
  const tv = useTV(); const enabled = !tv.demo && !!tv.selected?.online && !!tv.state?.homeVisible && !tv.busy;
  const modern = !!tv.state?.version;
  function key(value: Key) { void tv.perform({ type: 'navigate', key: value }).catch(report); }
  const control = (name: 'chevron-up' | 'chevron-down' | 'chevron-back' | 'chevron-forward' | 'home-outline' | 'arrow-undo-outline', value: Key) => <Pressable accessibilityRole="button" accessibilityLabel={value} disabled={!enabled} onPress={() => key(value)} style={({ pressed }) => [{ width: 74, height: 74, borderRadius: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.panel, opacity: enabled ? pressed ? .6 : 1 : .3 }]}><Icon name={name} size={27} /></Pressable>;
  return <Page eyebrow="THE GOOD KIND OF CONTROL" title="At your fingertips.">
    <Text style={[s.small, { marginBottom: 24 }]}>{tv.demo ? 'Design preview · pair a TV to enable the remote.' : tv.selected?.name ?? 'Pair a TV to enable the remote.'}</Text>
    <View style={{ alignItems: 'center', gap: 10, marginVertical: 12 }}>{control('chevron-up', 'up')}<View style={[s.row, { gap: 10 }]}>{control('chevron-back', 'left')}<Pressable accessibilityRole="button" accessibilityLabel="Select" disabled={!enabled} onPress={() => key('select')} style={({ pressed }) => [{ width: 88, height: 88, borderRadius: 32, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center', opacity: enabled ? pressed ? .6 : 1 : .3 }]}><Text style={{ fontSize: 17, fontWeight: '600', color: colors.bg }}>OK</Text></Pressable>{control('chevron-forward', 'right')}</View>{control('chevron-down', 'down')}<View style={[s.row, { gap: 45, marginTop: 17 }]}>{control('arrow-undo-outline', 'back')}{control('home-outline', 'home')}</View></View>
    {modern && <View style={[s.row, { gap: 10, marginTop: 10 }]}>{([['options-outline', 'Control Center', 'controlCenter'], ['albums-outline', 'Recent apps', 'recents'], ['apps-outline', 'All apps', 'apps']] as const).map(([icon, title, value]) => <Pressable key={value} accessibilityRole="button" accessibilityLabel={title} disabled={!enabled} onPress={() => key(value)} style={({ pressed }) => [{ flex: 1, alignItems: 'center', gap: 6, paddingVertical: 14, borderRadius: 18, backgroundColor: colors.panel, opacity: enabled ? pressed ? .6 : 1 : .3 }]}><Icon name={icon} size={22} /><Text style={[s.small, { fontSize: 11 }]}>{title}</Text></Pressable>)}</View>}
    <Notice>These controls work inside TV Home. Use the physical remote to return from another app. Volume and power controls are not included in this preview.</Notice>
    <Section title="Open an app" />{tv.state?.apps.map(app => <Pressable accessibilityRole="button" accessibilityLabel={`Open ${app.label}`} key={app.package} disabled={!enabled} onPress={() => void tv.perform({ type: 'launchApp', package: app.package }).catch(report)} style={{ marginBottom: 9, opacity: enabled ? 1 : .4 }}><Card><View style={s.row}><Icon name="apps-outline" size={20} color={colors.muted} /><Text style={[s.text, { flex: 1 }]}>{app.label}</Text><Icon name="arrow-up-right-box-outline" size={18} color={colors.muted} /></View></Card></Pressable>)}
    {!!tv.message && <Notice>{tv.message}</Notice>}
  </Page>;
}
