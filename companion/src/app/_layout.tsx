import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { TVProvider } from '../lib/store';
import { colors } from '../components/ui';
export default function Layout() {
  return <SafeAreaProvider><TVProvider><StatusBar style="light" /><Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.bg } }}><Stack.Screen name="index" /><Stack.Screen name="auth" /><Stack.Screen name="(tabs)" /><Stack.Screen name="pair" options={{ presentation: 'modal' }} /></Stack></TVProvider></SafeAreaProvider>;
}
