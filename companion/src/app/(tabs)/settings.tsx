import { useState } from 'react';
import { Alert, Pressable, Switch, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { api } from '../../lib/auth';
import { useTV } from '../../lib/store';
import { Button, Card, Icon, Notice, Page, Section, colors, report, s } from '../../components/ui';

export default function Settings() {
  const tv = useTV(); const [draft, setDraft] = useState<{ id: string | null; value: string } | null>(null);
  const name = draft?.id === tv.selectedId ? draft?.value ?? '' : tv.state?.roomName ?? '';
  const setName = (value: string) => setDraft({ id: tv.selectedId, value });
  const enabled = !!tv.state && (tv.demo || !!tv.selected?.online) && !tv.busy;
  function favorites(values: string[]) { void tv.perform({ type: 'settings', settings: { favorites: values } }).catch(report); }
  function move(index: number, direction: number) {
    const values = [...tv.state!.favorites]; const target = index + direction;
    [values[index], values[target]] = [values[target], values[index]]; favorites(values);
  }
  function removeTV() {
    Alert.alert('Disconnect this TV?', 'This account will lose control. You can pair it again from the TV.', [{ text: 'Cancel', style: 'cancel' }, { text: 'Disconnect', style: 'destructive', onPress: () => { void api(`/v1/devices/${tv.selectedId}`, { method: 'DELETE' }).then(tv.refreshDevices).catch(report); } }]);
  }
  return <Page eyebrow="THE WAY YOU LIKE IT" title="Make it yours.">
    {tv.demo && <Notice>Design preview. These changes stay in this preview and do not control a television.</Notice>}
    {tv.state && <><Section title="Your screen" /><Card><Text style={s.small}>Room name</Text><TextInput accessibilityLabel="TV room name" style={s.input} value={name} onChangeText={setName} maxLength={40} placeholder="Living room" placeholderTextColor={colors.muted} /><Button title="Save room name" disabled={!enabled || !name.trim() || name.trim() === tv.state.roomName} onPress={() => void tv.perform({ type: 'settings', settings: { roomName: name.trim() } }).catch(report)} /><View style={[s.row, { marginTop: 8 }]}><Text style={[s.text, { flex: 1 }]}>24-hour clock</Text><Switch accessibilityLabel="24-hour clock" value={tv.state.clock24h} disabled={!enabled} trackColor={{ true: '#5A765E', false: colors.line }} thumbColor={colors.accent} onValueChange={value => void tv.perform({ type: 'settings', settings: { clock24h: value } }).catch(report)} /></View></Card>
      <Section title="Background dimming" /><View style={s.row}>{[{ title: 'Light', value: .1 }, { title: 'Balanced', value: .3 }, { title: 'Dark', value: .55 }].map(item => <View key={item.title} style={{ flex: 1 }}><Button title={item.title} secondary={Math.abs(tv.state!.dim - item.value) > .01} disabled={!enabled} onPress={() => void tv.perform({ type: 'settings', settings: { dim: item.value } }).catch(report)} /></View>)}</View>
      <Section title="Your app dock" note={`${tv.state.favorites.length} / 8`} /><View style={{ gap: 9 }}>{tv.state.favorites.map((pkg, index) => <Card key={pkg} style={{ padding: 10 }}><View style={s.row}><Text style={[s.text, { flex: 1, marginLeft: 4 }]} numberOfLines={1}>{tv.state!.apps.find(a => a.package === pkg)?.label ?? pkg}</Text><Pressable accessibilityRole="button" accessibilityLabel="Move app earlier" disabled={!enabled || index === 0} style={{ padding: 10, opacity: index === 0 ? .25 : 1 }} onPress={() => move(index, -1)}><Icon name="chevron-up" size={18} /></Pressable><Pressable accessibilityRole="button" accessibilityLabel="Move app later" disabled={!enabled || index === tv.state!.favorites.length - 1} style={{ padding: 10, opacity: index === tv.state!.favorites.length - 1 ? .25 : 1 }} onPress={() => move(index, 1)}><Icon name="chevron-down" size={18} /></Pressable><Pressable accessibilityRole="button" accessibilityLabel="Remove app from favorites" disabled={!enabled} style={{ padding: 10 }} onPress={() => favorites(tv.state!.favorites.filter(p => p !== pkg))}><Icon name="close-outline" size={19} color={colors.muted} /></Pressable></View></Card>)}</View>
      <Section title="Add to favorites" />{tv.state.apps.filter(app => !tv.state!.favorites.includes(app.package)).map(app => <Pressable accessibilityRole="button" key={app.package} disabled={!enabled || tv.state!.favorites.length >= 8} onPress={() => favorites([...tv.state!.favorites, app.package])} style={[s.row, { paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: colors.line, opacity: tv.state!.favorites.length >= 8 ? .4 : 1 }]}><Text style={[s.text, { flex: 1 }]}>{app.label}</Text><Icon name="add-outline" color={colors.accent} /></Pressable>)}
    </>}
    <Section title="Account and devices" /><View style={{ gap: 12 }}><Button title="Add another TV" secondary icon="add-outline" onPress={() => router.push('/pair')} />{!tv.demo && tv.selected && <Button title="Disconnect this TV" secondary onPress={removeTV} />}<Button title={tv.demo ? 'Exit design preview' : 'Sign out'} secondary onPress={() => void tv.signOut().then(() => router.replace('/auth')).catch(report)} /></View>
    {tv.state && <Text style={[s.small, { marginTop: 20 }]}>{tv.state.model}{tv.state.sdk ? ` · Android API ${tv.state.sdk}` : ''}</Text>}{!!tv.message && <Notice>{tv.message}</Notice>}
  </Page>;
}
