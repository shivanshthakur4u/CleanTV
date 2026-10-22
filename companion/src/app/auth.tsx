import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, Pressable, Text, TextInput, View } from 'react-native';
import { Redirect, router } from 'expo-router';
import { auth, configured } from '../lib/auth';
import { useTV } from '../lib/store';
import { Atmosphere, Button, Icon, Notice, Page, colors, s } from '../components/ui';
export default function Auth() {
  const tv = useTV(); const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [signup, setSignup] = useState(false); const [loading, setLoading] = useState(false);
  if (tv.session || tv.demo) return <Redirect href="/(tabs)" />;
  async function submit() {
    if (!email.trim() || password.length < 8) return Alert.alert('Check your details', 'Enter an email address and a password of at least 8 characters.');
    setLoading(true);
    try {
      const { data, error } = signup ? await auth!.auth.signUp({ email: email.trim(), password }) : await auth!.auth.signInWithPassword({ email: email.trim(), password });
      if (error) throw error; setPassword('');
      if (signup && !data.session) Alert.alert('Check your email', 'Confirm your email address, then return here to sign in.');
    } catch (error) { Alert.alert('Could not sign in', error instanceof Error ? error.message : 'Try again.'); }
    finally { setLoading(false); }
  }
  return <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}><Page eyebrow="TV HOME" title="A little more you.">
    <Atmosphere scene="dusk" style={{ height: 190, borderRadius: 24, padding: 23, justifyContent: 'space-between', marginBottom: 23 }}><Icon name="tv-outline" size={36} /><Text style={{ color: '#EDF2E7', fontSize: 22, fontWeight: '300', lineHeight: 29 }}>Your space. Your screen.{`\n`}Beautifully in sync.</Text></Atmosphere>
    <Text style={[s.small, { marginBottom: 22 }]}>Make your TV feel at home. Change its atmosphere and arrange your favorites from wherever you are.</Text>
    {configured ? <View style={{ gap: 12 }}><TextInput accessibilityLabel="Email address" autoCapitalize="none" autoComplete="email" keyboardType="email-address" placeholder="Email address" placeholderTextColor={colors.muted} style={s.input} value={email} onChangeText={setEmail} /><TextInput accessibilityLabel="Password" secureTextEntry autoComplete={signup ? 'new-password' : 'current-password'} placeholder="Password" placeholderTextColor={colors.muted} style={s.input} value={password} onChangeText={setPassword} /><Button title={signup ? 'Create account' : 'Sign in'} loading={loading} onPress={() => void submit()} /><Pressable accessibilityRole="button" onPress={() => setSignup(v => !v)} style={{ padding: 10, alignItems: 'center' }}><Text style={s.small}>{signup ? 'Already have an account? Sign in' : 'New here? Create an account'}</Text></Pressable></View> : <Notice>Online services are not configured in this preview yet. You can explore the interface below.</Notice>}
    <Button title="Explore the design" secondary icon="sparkles-outline" onPress={() => { tv.startPreview(); router.replace('/(tabs)'); }} /><Text style={[s.small, { textAlign: 'center', marginTop: 14, fontSize: 10 }]}>Design preview works without an account or a connected TV.</Text>
  </Page></KeyboardAvoidingView>;
}
