import { Redirect } from 'expo-router';
import { ActivityIndicator, View } from 'react-native';
import { useTV } from '../lib/store';
import { colors } from '../components/ui';
export default function Index() {
  const tv = useTV();
  if (!tv.ready) return <View style={{ flex: 1, backgroundColor: colors.bg, justifyContent: 'center' }}><ActivityIndicator color={colors.accent} /></View>;
  return <Redirect href={tv.session || tv.demo ? '/(tabs)' : '/auth'} />;
}
