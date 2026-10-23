import { useState } from 'react';
import { Alert, Pressable, Switch, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { api } from '../../lib/auth';
import { MAX_FAVORITES, useTV, type Action, type Folder, type Settings as TVSettings } from '../../lib/store';
import { Button, Card, Choice, Field, Icon, Notice, Page, Section, colors, report, s } from '../../components/ui';

const actions: { value: Action; label: string }[] = [{ value: 'controlCenter', label: 'Control Center' }, { value: 'recents', label: 'Recent apps' }, { value: 'apps', label: 'All apps' }, { value: 'settings', label: 'Settings' }, { value: 'none', label: 'Nothing' }];

export default function Settings() {
  const tv = useTV();
  // Drafts remember which TV they were typed for, so switching TVs never saves text onto the wrong screen.
  const [draft, setDraft] = useState<{ id: string | null; field: string; value: string } | null>(null);
  const [folderName, setFolderName] = useState('');
  const [openFolder, setOpenFolder] = useState<string | null>(null);
  const text = (field: string, saved: string) => draft?.id === tv.selectedId && draft?.field === field ? draft.value : saved;
  const edit = (field: string) => (value: string) => setDraft({ id: tv.selectedId, field, value });
  const enabled = !!tv.state && (tv.demo || !!tv.selected?.online) && !tv.busy;
  const state = tv.state; const modern = state?.glass !== undefined;
  const apply = (settings: TVSettings) => tv.perform({ type: 'settings', settings }).then(() => setDraft(null)).catch(report);
  const label = (pkg: string) => state?.apps.find(a => a.package === pkg)?.label ?? pkg;
  const folders = state?.folders ?? []; const hidden = state?.hidden ?? [];
  const filed = new Set(folders.flatMap(f => f.apps));
  function favorites(values: string[]) { void apply({ favorites: values }); }
  function move(index: number, direction: number) {
    const values = [...state!.favorites]; const target = index + direction;
    [values[index], values[target]] = [values[target], values[index]]; favorites(values);
  }
  function saveFolders(next: Folder[]) { void apply({ folders: next }); }
  function removeTV() {
    Alert.alert('Disconnect this TV?', 'This account will lose control. You can pair it again from the TV.', [{ text: 'Cancel', style: 'cancel' }, { text: 'Disconnect', style: 'destructive', onPress: () => { void api(`/v1/devices/${tv.selectedId}`, { method: 'DELETE' }).then(tv.refreshDevices).catch(report); } }]);
  }
  function deleteAccount() {
    Alert.alert('Delete your account?', 'This removes your account and disconnects every paired TV. It cannot be undone.', [{ text: 'Cancel', style: 'cancel' }, { text: 'Delete account', style: 'destructive', onPress: () => { void tv.deleteAccount().then(() => router.replace('/auth')).catch(report); } }]);
  }
  const iconButton = (name: 'chevron-up' | 'chevron-down' | 'close-outline' | 'add-outline' | 'eye-off-outline' | 'eye-outline' | 'folder-outline' | 'trash-outline', a11y: string, onPress: () => void, disabled = false, color: string = colors.text) =>
    <Pressable accessibilityRole="button" accessibilityLabel={a11y} disabled={!enabled || disabled} style={{ padding: 9, opacity: !enabled || disabled ? .25 : 1 }} onPress={onPress}><Icon name={name} size={19} color={color} /></Pressable>;

  return <Page eyebrow="THE WAY YOU LIKE IT" title="Make it yours.">
    {tv.demo && <Notice>Design preview. These changes stay in this preview and do not control a television.</Notice>}
    {state && <>
      <Section title="Your screen" /><Card>
        <Text style={s.small}>Room name</Text>
        <TextInput accessibilityLabel="TV room name" style={s.input} value={text('room', state.roomName)} onChangeText={edit('room')} maxLength={40} placeholder="Living room" placeholderTextColor={colors.muted} />
        <Button title="Save room name" disabled={!enabled || !text('room', state.roomName).trim() || text('room', state.roomName).trim() === state.roomName} onPress={() => void apply({ roomName: text('room', state.roomName).trim() })} />
        <View style={[s.row, { marginTop: 8 }]}><Text style={[s.text, { flex: 1 }]}>24-hour clock</Text><Switch accessibilityLabel="24-hour clock" value={state.clock24h} disabled={!enabled} trackColor={{ true: '#5A765E', false: colors.line }} thumbColor={colors.accent} onValueChange={clock24h => void apply({ clock24h })} /></View>
      </Card>

      {modern && <>
        <Section title="Weather" note="Open-Meteo" /><Card>
          <Text style={s.small}>City for the weather pill and sunset Dark Mode. Leave empty to turn weather off.</Text>
          <TextInput accessibilityLabel="Weather city" style={s.input} value={text('city', state.weatherCity ?? '')} onChangeText={edit('city')} maxLength={60} placeholder="e.g. Mumbai" placeholderTextColor={colors.muted} autoCapitalize="words" />
          <Button title="Save city" disabled={!enabled || text('city', state.weatherCity ?? '').trim() === state.weatherCity} onPress={() => void apply({ weatherCity: text('city', state.weatherCity ?? '').trim() })} />
          {state.weather && <Text style={s.small}>Now {state.weather.temp}° in {state.weather.place}</Text>}
          <Choice disabled={!enabled} value={state.tempUnit} onChange={tempUnit => void apply({ tempUnit })} options={[{ value: 'c', label: 'Celsius' }, { value: 'f', label: 'Fahrenheit' }]} />
        </Card>
        <Section title="Remote buttons" note="Inside TV Home" /><Card>
          <Field label="Menu button"><Choice disabled={!enabled} value={state.menuAction} onChange={menuAction => void apply({ menuAction })} options={actions} /></Field>
          <Field label="Info or Guide button"><Choice disabled={!enabled} value={state.infoAction} onChange={infoAction => void apply({ infoAction })} options={actions} /></Field>
        </Card>
      </>}

      <Section title="Favourites shelf" note={`${state.favorites.length} / ${MAX_FAVORITES}`} /><View style={{ gap: 9 }}>{state.favorites.map((pkg, index) => <Card key={pkg} style={{ padding: 8 }}><View style={s.row}>
        <Text style={[s.text, { flex: 1, marginLeft: 6 }]} numberOfLines={1}>{label(pkg)}</Text>
        {iconButton('chevron-up', 'Move app earlier', () => move(index, -1), index === 0)}
        {iconButton('chevron-down', 'Move app later', () => move(index, 1), index === state.favorites.length - 1)}
        {iconButton('close-outline', 'Remove app from favourites', () => favorites(state.favorites.filter(p => p !== pkg)), false, colors.muted)}
      </View></Card>)}</View>

      {modern && <>
        <Section title="Folders" note={`${folders.length} / 12`} />
        <View style={{ gap: 9 }}>{folders.map(folder => <Card key={folder.name} style={{ padding: 12 }}>
          <View style={s.row}><Icon name="folder-outline" size={19} color={colors.accent} /><Pressable accessibilityRole="button" style={{ flex: 1 }} onPress={() => setOpenFolder(openFolder === folder.name ? null : folder.name)}><Text style={s.text}>{folder.name}</Text><Text style={s.small}>{folder.apps.length ? folder.apps.map(label).join(', ') : 'Empty'}</Text></Pressable>
            {iconButton('trash-outline', `Delete folder ${folder.name}`, () => saveFolders(folders.filter(f => f.name !== folder.name)), false, colors.muted)}</View>
          {openFolder === folder.name && <View style={{ gap: 2 }}>
            {folder.apps.map(pkg => <View key={pkg} style={s.row}><Text style={[s.small, { flex: 1 }]}>{label(pkg)}</Text>{iconButton('close-outline', `Remove ${label(pkg)} from folder`, () => saveFolders(folders.map(f => f.name === folder.name ? { ...f, apps: f.apps.filter(p => p !== pkg) } : f)), false, colors.muted)}</View>)}
            <Text style={[s.small, { marginTop: 8 }]}>Add apps</Text>
            {state.apps.filter(app => !filed.has(app.package)).map(app => <View key={app.package} style={s.row}><Text style={[s.text, { flex: 1 }]} numberOfLines={1}>{app.label}</Text>{iconButton('add-outline', `Add ${app.label} to ${folder.name}`, () => saveFolders(folders.map(f => f.name === folder.name ? { ...f, apps: [...f.apps, app.package] } : f)), folder.apps.length >= 24, colors.accent)}</View>)}
          </View>}
        </Card>)}</View>
        <View style={[s.row, { marginTop: 12 }]}><TextInput accessibilityLabel="New folder name" style={[s.input, { flex: 1 }]} value={folderName} onChangeText={setFolderName} maxLength={24} placeholder="New folder name" placeholderTextColor={colors.muted} />
          <Pressable accessibilityRole="button" accessibilityLabel="Create folder" disabled={!enabled || !folderName.trim() || folders.length >= 12 || folders.some(f => f.name === folderName.trim())} onPress={() => { saveFolders([...folders, { name: folderName.trim(), apps: [] }]); setOpenFolder(folderName.trim()); setFolderName(''); }} style={{ padding: 14, borderRadius: 14, backgroundColor: colors.accent, opacity: !enabled || !folderName.trim() ? .4 : 1 }}><Icon name="add-outline" color={colors.bg} /></Pressable></View>
      </>}

      <Section title="All apps" note={modern ? 'Add to shelf or hide' : 'Add to shelf'} />
      {state.apps.map(app => {
        const isFavorite = state.favorites.includes(app.package); const isHidden = hidden.includes(app.package);
        return <View key={app.package} style={[s.row, { paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: colors.line }]}>
          <Text style={[s.text, { flex: 1, opacity: isHidden ? .45 : 1 }]} numberOfLines={1}>{app.label}{isHidden ? '  ·  hidden' : ''}</Text>
          {modern && iconButton(isHidden ? 'eye-outline' : 'eye-off-outline', isHidden ? `Show ${app.label}` : `Hide ${app.label}`, () => void apply({ hidden: isHidden ? hidden.filter(p => p !== app.package) : [...hidden, app.package] }), false, colors.muted)}
          {iconButton('add-outline', `Add ${app.label} to favourites`, () => favorites([...state.favorites, app.package]), isFavorite || state.favorites.length >= (modern ? MAX_FAVORITES : 8), colors.accent)}
        </View>;
      })}
    </>}
    <Section title="Account and devices" /><View style={{ gap: 12 }}>
      <Button title="Add another TV" secondary icon="add-outline" onPress={() => router.push('/pair')} />
      {!tv.demo && tv.selected && <Button title="Disconnect this TV" secondary onPress={removeTV} />}
      <Button title={tv.demo ? 'Exit design preview' : 'Sign out'} secondary onPress={() => void tv.signOut().then(() => router.replace('/auth')).catch(report)} />
      {!tv.demo && tv.session && <Pressable accessibilityRole="button" onPress={deleteAccount} style={{ padding: 12, alignItems: 'center' }}><Text style={{ color: '#E8A0A0', fontSize: 13 }}>Delete account</Text></Pressable>}
    </View>
    {state && <Text style={[s.small, { marginTop: 20 }]}>{state.model}{state.sdk ? ` · Android API ${state.sdk}` : ''}{state.version ? ` · TV Home ${state.version}` : ''}{state.ram ? ` · ${(state.ram.available / 1e9).toFixed(1)} GB of ${(state.ram.total / 1e9).toFixed(1)} GB RAM free` : ''}</Text>}
    {!!tv.message && <Notice>{tv.message}</Notice>}
  </Page>;
}
