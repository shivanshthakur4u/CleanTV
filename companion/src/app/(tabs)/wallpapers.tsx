import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import { useTV, type Settings } from '../../lib/store';
import { Atmosphere, Button, Card, Choice, Field, Icon, Notice, Page, Section, TVPreview, clockLabel, colors, report, s, scenes } from '../../components/ui';

export default function Wallpapers() {
  const tv = useTV(); const [uploading, setUploading] = useState(false);
  const enabled = !!tv.state && (tv.demo || !!tv.selected?.online) && !tv.busy && !uploading;
  const modern = tv.state?.glass !== undefined;
  const apply = (settings: Settings) => void tv.perform({ type: 'settings', settings }).catch(report);
  async function upload() {
    try {
      const picked = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 1, allowsMultipleSelection: false });
      if (picked.canceled) return;
      setUploading(true);
      const photo = picked.assets[0];
      const context = ImageManipulator.manipulate(photo.uri);
      const width = Math.min(photo.width, Math.floor(photo.height * 16 / 9));
      const height = Math.min(photo.height, Math.floor(photo.width * 9 / 16));
      context.crop({ originX: Math.floor((photo.width - width) / 2), originY: Math.floor((photo.height - height) / 2), width, height }).resize({ width: Math.min(width, 1600) });
      const rendered = await context.renderAsync();
      const result = await rendered.saveAsync({ format: SaveFormat.JPEG, compress: .78, base64: true });
      if (!result.base64 || result.base64.length > 2800000) throw new Error('This photo is too large. Try a smaller image.');
      await tv.perform({ type: 'wallpaper', data: result.base64 });
    } catch (error) { report(error); } finally { setUploading(false); }
  }
  const step = (minutes: number, delta: number) => (minutes + delta + 1440) % 1440;
  const time = (key: 'darkStart' | 'darkEnd', title: string) => {
    const value = tv.state?.[key] ?? 0;
    return <View style={[s.row, { justifyContent: 'space-between' }]}><Text style={s.text}>{title}</Text><View style={s.row}>
      <Pressable accessibilityRole="button" accessibilityLabel={`${title} 30 minutes earlier`} disabled={!enabled} onPress={() => apply({ [key]: step(value, -30) })} style={{ padding: 8, opacity: enabled ? 1 : .4 }}><Icon name="remove-circle-outline" /></Pressable>
      <Text style={[s.text, { fontVariant: ['tabular-nums'], minWidth: 52, textAlign: 'center' }]}>{clockLabel(value)}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel={`${title} 30 minutes later`} disabled={!enabled} onPress={() => apply({ [key]: step(value, 30) })} style={{ padding: 8, opacity: enabled ? 1 : .4 }}><Icon name="add-circle-outline" /></Pressable>
    </View></View>;
  };
  return <Page eyebrow="A CHANGE OF SCENERY" title="Find your style.">
    {tv.state ? <TVPreview state={tv.state} /> : <Notice>Pair a TV to change its atmosphere.</Notice>}
    {tv.state && modern && <>
      <Section title="Look and feel" note={tv.state.darkActive ? 'Dark now' : 'Light now'} />
      <Card>
        <Field label="Glass style"><Choice disabled={!enabled} value={tv.state.glass} onChange={glass => apply({ glass })} options={[{ value: 'liquid', label: 'Liquid Glass' }, { value: 'frosted', label: 'Frosted Glass' }]} /></Field>
        <Field label="Overall blur of menus and Control Center"><Choice disabled={!enabled} value={tv.state.blur} onChange={blur => apply({ blur })} options={[0, 5, 10, 15, 20, 25].map(value => ({ value, label: value ? String(value) : 'Off' }))} /></Field>
        <Field label="Dark Mode"><Choice disabled={!enabled} value={tv.state.darkMode} onChange={darkMode => apply({ darkMode })} options={[{ value: 'off', label: 'Off' }, { value: 'on', label: 'On' }, { value: 'sunset', label: 'Sunset to sunrise' }, { value: 'custom', label: 'Custom' }]} /></Field>
        {tv.state.darkMode === 'sunset' && !tv.state.weatherCity && <Text style={[s.small, { marginTop: -8, marginBottom: 12 }]}>Set a weather city in Settings for local sunset times. Until then, 19:00 to 07:00 is used.</Text>}
        {tv.state.darkMode === 'custom' && <View style={{ marginTop: -6, marginBottom: 12 }}>{time('darkStart', 'Dark from')}{time('darkEnd', 'Light from')}</View>}
        <Field label="Status pill"><Choice disabled={!enabled} value={tv.state.pill} onChange={pill => apply({ pill })} options={[{ value: 'minimal', label: 'Minimal' }, { value: 'full', label: 'Full' }, { value: 'weather', label: 'Weather' }, { value: 'hidden', label: 'Hidden' }]} /></Field>
        <Field label="App tiles"><Choice disabled={!enabled} value={tv.state.iconStyle} onChange={iconStyle => apply({ iconStyle })} options={[{ value: 'banner', label: 'TV banners' }, { value: 'icon', label: 'Icons' }]} /></Field>
      </Card>
    </>}
    {tv.state && !modern && <Notice>Update TV Home on the television to 0.2 or later for Liquid Glass, Dark Mode, and the adaptive background.</Notice>}
    <Section title="Backgrounds" /><View style={{ gap: 14 }}>
      {scenes.filter(scene => scene.id !== 'adaptive' || modern).map(scene => <Pressable key={scene.id} accessibilityRole="button" accessibilityLabel={`Apply ${scene.title}`} accessibilityState={{ selected: tv.state?.scene === scene.id, disabled: !enabled }} disabled={!enabled} onPress={() => apply({ scene: scene.id })} style={{ opacity: enabled ? 1 : .45 }}><Atmosphere scene={scene.id} style={{ height: 110, borderRadius: 19, padding: 18, justifyContent: 'flex-end', borderWidth: 1, borderColor: tv.state?.scene === scene.id ? colors.accent : '#FFFFFF17' }}><View style={[s.row, { justifyContent: 'space-between' }]}><View><Text style={[s.text, { fontSize: 19 }]}>{scene.title}</Text><Text style={{ color: '#E1E8DB', fontSize: 11, marginTop: 4 }}>{scene.subtitle}</Text></View>{tv.state?.scene === scene.id && <Icon name="checkmark-circle" color={colors.accent} />}</View></Atmosphere></Pressable>)}
    </View>
    {tv.state && <><Section title="Background dimming" /><Choice disabled={!enabled} value={[0, .1, .2, .3, .45, .6].find(v => Math.abs(v - tv.state!.dim) < .01)} onChange={dim => apply({ dim })} options={[0, .1, .2, .3, .45, .6].map(value => ({ value, label: `${Math.round(value * 100)}%` }))} /></>}
    <Section title="Something personal" /><Button title="Choose a photo" icon="add-outline" secondary disabled={!enabled} loading={uploading} onPress={() => void upload()} />
    <Text style={[s.small, { marginTop: 12 }]}>Photos are center-cropped to 16:9 and resized for the TV. Dimming keeps your apps readable.</Text>{!!tv.message && <Notice>{tv.message}</Notice>}
  </Page>;
}
