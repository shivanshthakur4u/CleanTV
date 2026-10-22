import { useEffect, useState } from 'react';
import { ActivityIndicator, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { api } from '../lib/auth';
import { useTV } from '../lib/store';
import { Button, Card, Icon, Notice, Page, colors, report, s } from '../components/ui';

export default function Pair() {
  const tv = useTV(); const [code, setCode] = useState(''); const [requestId, setRequestId] = useState<string | null>(null); const [loading, setLoading] = useState(false); const [message, setMessage] = useState('');
  const { refreshDevices, select } = tv;
  useEffect(() => {
    if (!requestId) return;
    let stopped = false, inFlight = false;
    const timer = setInterval(() => {
      if (inFlight || stopped) return;
      inFlight = true;
      void api<{ status: string; deviceId: string }>(`/v1/pair/${requestId}`).then(async result => {
        if (stopped) return;
        if (result.status === 'approved') { clearInterval(timer); await refreshDevices(); select(result.deviceId); router.replace('/(tabs)'); }
        else if (result.status !== 'pending') { setMessage(result.status === 'declined' ? 'Pairing was declined on the TV.' : 'Pairing expired. Get a new code on the TV.'); setRequestId(null); }
      }).catch(error => { if (!stopped) { setMessage(error.message); setRequestId(null); } }).finally(() => { inFlight = false; });
    }, 1500);
    return () => { stopped = true; clearInterval(timer); };
  }, [requestId, refreshDevices, select]);
  async function pair() {
    setLoading(true); setMessage('');
    try { const result = await api<{ requestId: string }>('/v1/pair', { method: 'POST', body: JSON.stringify({ code }) }); setRequestId(result.requestId); }
    catch (error) { report(error); } finally { setLoading(false); }
  }
  return <Page eyebrow="BETTER TOGETHER" title="Meet your TV."><Card><Icon name="tv-outline" size={46} color={colors.accent} /><Text style={[s.text, { fontSize: 22 }]}>One screen. Your account.</Text><Text style={s.small}>On your television, open TV Home and choose Connect phone. Enter the 10-character code, then approve your account on the TV.</Text></Card>
    {tv.demo || !tv.session ? <><Notice>Exit the design preview and sign in to pair a real television.</Notice><Button title="Go to sign in" onPress={() => void tv.signOut().then(() => router.replace('/auth')).catch(report)} /></> : requestId ? <View style={{ marginVertical: 30, gap: 18 }}><ActivityIndicator color={colors.accent} /><Text style={[s.text, { textAlign: 'center' }]}>Approve this account on your TV.</Text><Text style={[s.small, { textAlign: 'center' }]}>This request expires in two minutes.</Text><Button title="Cancel pairing" secondary onPress={() => { void api(`/v1/pair/${requestId}`, { method: 'DELETE' }).then(() => setRequestId(null)).catch(report); }} /></View> : <View style={{ gap: 14, marginTop: 25 }}><TextInput accessibilityLabel="TV pairing code" style={[s.input, { fontSize: 25, letterSpacing: 4, textAlign: 'center', fontVariant: ['tabular-nums'] }]} placeholder="A1B2C3D4E5" placeholderTextColor={colors.muted} autoCapitalize="characters" autoCorrect={false} value={code} onChangeText={v => setCode(v.toUpperCase().replace(/[^A-F0-9]/g, '').slice(0, 10))} maxLength={11} /><Button title="Pair with TV" loading={loading} disabled={code.length !== 10} onPress={() => void pair()} /></View>}
    {!!message && <Notice>{message}</Notice>}<View style={{ marginTop: 20 }}><Button title="Back" secondary onPress={() => router.back()} /></View>
  </Page>;
}
