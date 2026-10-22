import { Redirect, Tabs } from 'expo-router';
import { useTV } from '../../lib/store';
import { Icon, colors } from '../../components/ui';
export default function TabsLayout() {
  const tv = useTV();
  if (tv.ready && !tv.session && !tv.demo) return <Redirect href="/auth" />;
  return <Tabs screenOptions={{ headerShown: false, tabBarActiveTintColor: colors.accent, tabBarInactiveTintColor: '#788D83', tabBarStyle: { backgroundColor: colors.bg, borderTopColor: colors.line, paddingTop: 9 }, tabBarLabelStyle: { fontSize: 10, marginTop: 3 } }}>
    <Tabs.Screen name="index" options={{ title: 'Home', tabBarIcon: ({ color }) => <Icon name="home-outline" color={color} /> }} />
    <Tabs.Screen name="wallpapers" options={{ title: 'Atmosphere', tabBarIcon: ({ color }) => <Icon name="images-outline" color={color} /> }} />
    <Tabs.Screen name="remote" options={{ title: 'Remote', tabBarIcon: ({ color }) => <Icon name="navigate-circle-outline" color={color} /> }} />
    <Tabs.Screen name="settings" options={{ title: 'Settings', tabBarIcon: ({ color }) => <Icon name="options-outline" color={color} /> }} />
  </Tabs>;
}
