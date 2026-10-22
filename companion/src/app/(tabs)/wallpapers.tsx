import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import { useTV } from '../../lib/store';
import { Atmosphere, Button, Icon, Notice, Page, Section, TVPreview, colors, report, s, scenes } from '../../components/ui';

export default function Wallpapers() {
  const tv = useTV(); const [uploading, setUploading] = useState(false);
  const enabled = !!tv.state && (tv.demo || !!tv.selected?.online) && !tv.busy && !uploading;
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
  return <Page eyebrow="A CHANGE OF SCENERY" title="Find your calm.">
    {tv.state ? <TVPreview state={tv.state} /> : <Notice>Pair a TV to change its atmosphere.</Notice>}
    <Section title="Atmospheres" note="Four quiet places" /><View style={{ gap: 14 }}>
      {scenes.map(scene => <Pressable key={scene.id} accessibilityRole="button" accessibilityLabel={`Apply ${scene.title}`} accessibilityState={{ selected: tv.state?.scene === scene.id, disabled: !enabled }} disabled={!enabled} onPress={() => void tv.perform({ type: 'settings', settings: { scene: scene.id } }).catch(report)} style={{ opacity: enabled ? 1 : .45 }}><Atmosphere scene={scene.id} style={{ height: 125, borderRadius: 19, padding: 18, justifyContent: 'flex-end', borderWidth: 1, borderColor: tv.state?.scene === scene.id ? colors.accent : '#FFFFFF17' }}><View style={[s.row, { justifyContent: 'space-between' }]}><View><Text style={[s.text, { fontSize: 19 }]}>{scene.title}</Text><Text style={{ color: '#E1E8DB', fontSize: 11, marginTop: 4 }}>{scene.subtitle}</Text></View>{tv.state?.scene === scene.id && <Icon name="checkmark-circle" color={colors.accent} />}</View></Atmosphere></Pressable>)}
    </View><Section title="Something personal" /><Button title="Choose a photo" icon="add-outline" secondary disabled={!enabled} loading={uploading} onPress={() => void upload()} />
    <Text style={[s.small, { marginTop: 12 }]}>Photos are center-cropped to 16:9 and resized for the TV. A dark overlay keeps your apps readable.</Text>{!!tv.message && <Notice>{tv.message}</Notice>}
  </Page>;
}
