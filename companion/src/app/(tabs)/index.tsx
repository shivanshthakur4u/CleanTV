import { Alert, Pressable, Text, View } from 'react-native';
import { router } from 'expo-router';
import { useTV } from '../../lib/store';
import { Button, Card, Icon, Notice, Page, Section, TVPreview, colors, report, s } from '../../components/ui';

export default function Home() {
  const tv = useTV();
  function chooseTV() {
    Alert.alert('Your televisions', 'Choose the screen you want to make yours.', [...tv.devices.map(d => ({ text: d.name, onPress: () => tv.select(d.id) })), { text: 'Add a TV', onPress: () => router.push('/pair') }, { text: 'Cancel', style: 'cancel' as const }]);
  }
  return <Page eyebrow="YOUR SPACE, ON SCREEN" title="Welcome home." right={<Pressable accessibilityRole="button" accessibilityLabel="Add a TV" onPress={() => router.push('/pair')} style={{ padding: 12, backgroundColor: colors.panel, borderRadius: 18 }}><Icon name="add-outline" /></Pressable>}>
    {tv.selected ? <>
      <Pressable accessibilityRole="button" onPress={chooseTV} style={[s.row, { marginBottom: 18 }]}><Icon name="tv-outline" size={19} /><Text style={[s.text, { flex: 1 }]}>{tv.state?.roomName ?? tv.selected.name}</Text><View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: tv.demo ? colors.amber : tv.selected.online ? colors.accent : colors.muted }} /><Text style={s.small}>{tv.demo ? 'Preview' : tv.selected.online ? 'Online' : 'Offline'}</Text><Icon name="chevron-down" size={15} /></Pressable>
      {tv.state ? <TVPreview state={tv.state} /> : <Card><Text style={s.text}>{tv.selected.online ? 'Loading your TV…' : 'Your TV is offline'}</Text><Text style={s.small}>Turn on the television and check its internet connection to see its current settings.</Text></Card>}
      <View style={[s.row, { justifyContent: 'space-between', marginTop: 12 }]}><Text style={s.small}>Layout preview</Text><Pressable accessibilityRole="button" accessibilityLabel="Refresh TV" style={{ padding: 8 }} onPress={() => { void tv.refreshDevices().then(tv.refreshState).catch(report); }}><Icon name="refresh-outline" size={17} color={colors.muted} /></Pressable></View>
      <Section title="Set the mood" note="Make it yours" /><View style={{ gap: 12 }}><Button title="Change atmosphere" icon="images-outline" onPress={() => router.push('/wallpapers')} /><Button title="Arrange your favorite apps" secondary icon="grid-outline" onPress={() => router.push('/settings')} /></View>
      <Section title="A calmer kind of home" /><Card><View style={s.row}><Icon name="leaf-outline" color={colors.accent} /><Text style={s.text}>Only what you choose.</Text></View><Text style={s.small}>Your atmosphere, your favorite apps, and room to breathe. Extra controls stay one tap away.</Text></Card>
    </> : <Card><Icon name="tv-outline" size={42} color={colors.accent} /><Text style={[s.text, { fontSize: 23, marginTop: 10 }]}>A fresh start for your TV.</Text><Text style={s.small}>Open TV Home on your television, choose Connect phone, and enter its pairing code here.</Text><Button title="Pair your first TV" onPress={() => router.push('/pair')} /></Card>}
    {!!tv.message && <Notice>{tv.message}</Notice>}
  </Page>;
}
