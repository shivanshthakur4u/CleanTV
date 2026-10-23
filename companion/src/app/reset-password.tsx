import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import * as Linking from 'expo-linking';
import { auth } from '../lib/auth';
import { Button, Notice, Page, colors, s } from '../components/ui';

// Supabase's recovery email links back here with the recovery session in the URL fragment.
function recovery(url: string) {
  const params = new URLSearchParams(url.split('#')[1] ?? url.split('?')[1] ?? '');
  const failure = params.get('error_description');
  if (failure) return { error: failure.replace(/\+/g, ' ') };
  const access = params.get('access_token'); const refresh = params.get('refresh_token');
  if (!access || !refresh || params.get('type') !== 'recovery') return { error: 'This reset link is invalid or has already been used. Request a new one.' };
  return { access, refresh };
}

export default function ResetPassword() {
  const url = Linking.useURL();
  const link = url ? recovery(url) : null;
  const [session, setSession] = useState<{ url: string; ok: boolean; problem?: string } | null>(null);
  const linkError = link && 'error' in link ? link.error : undefined;
  const stage = linkError ? 'failed' : session?.url !== url || !session ? 'waiting' : session.ok ? 'ready' : 'failed';
  const problem = linkError ?? session?.problem ?? '';
  const [password, setPassword] = useState(''); const [confirm, setConfirm] = useState(''); const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (!url || !auth) return;
    const link = recovery(url);
    if ('error' in link) return;
    void auth.auth.setSession({ access_token: link.access, refresh_token: link.refresh }).then(({ error }) => setSession({ url, ok: !error, problem: error?.message }));
  }, [url]);
  async function save() {
    if (password.length < 8) return Alert.alert('Choose a longer password', 'Use at least 8 characters.');
    if (password !== confirm) return Alert.alert('Passwords do not match', 'Type the same password twice.');
    setLoading(true);
    try {
      const { error } = await auth!.auth.updateUser({ password });
      if (error) throw error;
      Alert.alert('Password updated', 'You are signed in with your new password.');
      router.replace('/(tabs)');
    } catch (error) { Alert.alert('Could not update the password', error instanceof Error ? error.message : 'Try again.'); }
    finally { setLoading(false); }
  }
  return <Page eyebrow="ACCOUNT" title="New password.">
    {stage === 'waiting' && <View style={{ gap: 16, marginTop: 20 }}><ActivityIndicator color={colors.accent} /><Text style={[s.small, { textAlign: 'center' }]}>Checking your reset link…</Text></View>}
    {stage === 'failed' && <Notice>{problem}</Notice>}
    {stage === 'ready' && <View style={{ gap: 12 }}>
      <TextInput accessibilityLabel="New password" secureTextEntry autoComplete="new-password" placeholder="New password" placeholderTextColor={colors.muted} style={s.input} value={password} onChangeText={setPassword} />
      <TextInput accessibilityLabel="Confirm new password" secureTextEntry autoComplete="new-password" placeholder="Confirm new password" placeholderTextColor={colors.muted} style={s.input} value={confirm} onChangeText={setConfirm} />
      <Button title="Save new password" loading={loading} onPress={() => void save()} />
    </View>}
    <View style={{ marginTop: 20 }}><Button title="Back to sign in" secondary onPress={() => router.replace('/auth')} /></View>
  </Page>;
}
