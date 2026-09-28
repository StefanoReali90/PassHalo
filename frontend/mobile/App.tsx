import { StatusBar as ExpoStatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState, type ComponentType } from 'react';
import { ActivityIndicator, Platform, Pressable, SafeAreaView, StatusBar, StyleSheet, Text, View } from 'react-native';
import { BarChart3, CalendarCheck, CalendarDays, LogIn, LogOut, QrCode, Settings, Ticket, UserPlus } from 'lucide-react-native';
import { AuthProvider, useAuth } from './src/context/AuthContext';
import { api } from './src/api';
import { BookingScreen } from './src/screens/BookingScreen';
import { BookingsScreen } from './src/screens/BookingsScreen';
import { DashboardScreen } from './src/screens/DashboardScreen';
import { EventsScreen } from './src/screens/EventsScreen';
import { LoginScreen } from './src/screens/LoginScreen';
import { RegisterScreen } from './src/screens/RegisterScreen';
import { ScannerScreen } from './src/screens/ScannerScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { colors, spacing } from './src/theme';

type ScreenName = 'booking' | 'login' | 'register' | 'dashboard' | 'events' | 'scanner' | 'bookings' | 'settings';
type Icon = ComponentType<{ color?: string; size?: number }>;

interface TabDefinition {
  key: ScreenName;
  label: string;
  icon: Icon;
}

const publicTabs: TabDefinition[] = [
  { key: 'booking', label: 'Prenota', icon: Ticket },
  { key: 'login', label: 'Accedi', icon: LogIn },
  { key: 'register', label: 'Registrati', icon: UserPlus },
  { key: 'settings', label: 'Server', icon: Settings },
];

const adminTabs: TabDefinition[] = [
  { key: 'dashboard', label: 'Dashboard', icon: BarChart3 },
  { key: 'events', label: 'Eventi', icon: CalendarDays },
  { key: 'scanner', label: 'Scanner', icon: QrCode },
  { key: 'bookings', label: 'Pass', icon: CalendarCheck },
  { key: 'settings', label: 'Server', icon: Settings },
];

const staffTabs: TabDefinition[] = [
  { key: 'scanner', label: 'Scanner', icon: QrCode },
  { key: 'settings', label: 'Server', icon: Settings },
];

function PassHaloApp() {
  const { user, loading, logout } = useAuth();
  const [screen, setScreen] = useState<ScreenName>('booking');
  const [eventAdminAccess, setEventAdminAccess] = useState<{ email: string; allowed: boolean } | null>(null);

  const canManageEvents = !!user && (user.role === 'ADMIN' ||
    (eventAdminAccess?.email === user.email && eventAdminAccess.allowed));
  const tabs = useMemo(() => canManageEvents ? adminTabs : user?.role === 'STAFF' ? staffTabs : publicTabs,
    [canManageEvents, user]);

  useEffect(() => {
    if (!user || user.role === 'ADMIN') {
      setEventAdminAccess(null);
      return;
    }
    let active = true;
    api.myEvents().then((events) => {
      if (active) setEventAdminAccess({ email: user.email, allowed: events.some((event) => event.role === 'EVENT_ADMIN') });
    }).catch(() => {
      if (active) setEventAdminAccess({ email: user.email, allowed: false });
    });
    return () => { active = false; };
  }, [user]);

  useEffect(() => {
    if (!user && !publicTabs.some((tab) => tab.key === screen)) setScreen('booking');
    if (user?.role === 'ADMIN' && !adminTabs.some((tab) => tab.key === screen)) setScreen('dashboard');
    if (user?.role === 'STAFF' && !tabs.some((tab) => tab.key === screen)) setScreen('scanner');
  }, [screen, tabs, user]);

  if (loading) {
    return (
      <View style={styles.splash}>
        <Ticket color={colors.accent} size={48} />
        <Text style={styles.splashBrand}>passhalo<Text style={styles.dot}>.</Text></Text>
        <ActivityIndicator color={colors.accent} />
      </View>
    );
  }

  const openAfterLogin = (role: 'ADMIN' | 'STAFF') => setScreen(role === 'ADMIN' ? 'dashboard' : 'scanner');

  return (
    <SafeAreaView style={styles.safeArea}>
      <ExpoStatusBar style="light" />
      <View style={styles.header}>
        <View style={styles.brandRow}>
          <Ticket color={colors.accent} size={25} />
          <Text style={styles.brand}>passhalo<Text style={styles.dot}>.</Text></Text>
        </View>
        {user ? (
          <View style={styles.accountRow}>
            <View style={styles.accountCopy}>
              <Text numberOfLines={1} style={styles.accountName}>{user.name}</Text>
              <Text style={styles.accountRole}>{user.role}</Text>
            </View>
            <Pressable accessibilityLabel="Esci" onPress={() => void logout()} style={styles.logoutButton}>
              <LogOut color={colors.muted} size={19} />
            </Pressable>
          </View>
        ) : null}
      </View>

      <View style={styles.content}>
        {screen === 'booking' ? <BookingScreen onOpenSettings={() => setScreen('settings')} /> : null}
        {screen === 'login' ? <LoginScreen onOpenSettings={() => setScreen('settings')} onLoggedIn={(loggedUser) => openAfterLogin(loggedUser.role)} /> : null}
        {screen === 'register' ? <RegisterScreen onLogin={() => setScreen('login')} onOpenSettings={() => setScreen('settings')} /> : null}
        {screen === 'dashboard' && canManageEvents ? <DashboardScreen /> : null}
        {screen === 'events' && canManageEvents ? <EventsScreen canCreateEvent={user?.role === 'ADMIN'} /> : null}
        {screen === 'scanner' && user ? <ScannerScreen /> : null}
        {screen === 'bookings' && canManageEvents ? <BookingsScreen /> : null}
        {screen === 'settings' ? <SettingsScreen /> : null}
      </View>

      <View style={styles.tabBar}>
        {tabs.map(({ key, label, icon: TabIcon }) => {
          const active = screen === key;
          return (
            <Pressable key={key} onPress={() => setScreen(key)} style={styles.tab} accessibilityRole="button" accessibilityState={{ selected: active }}>
              <TabIcon color={active ? colors.accent : colors.muted} size={21} />
              <Text numberOfLines={1} style={[styles.tabLabel, active && styles.tabLabelActive]}>{label}</Text>
            </Pressable>
          );
        })}
      </View>
    </SafeAreaView>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <PassHaloApp />
    </AuthProvider>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background, paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0 },
  splash: { flex: 1, backgroundColor: colors.background, alignItems: 'center', justifyContent: 'center', gap: spacing.md },
  splashBrand: { color: colors.text, fontSize: 32, fontWeight: '900', letterSpacing: -1 },
  dot: { color: colors.accent },
  header: { minHeight: 64, paddingHorizontal: spacing.lg, borderBottomColor: colors.border, borderBottomWidth: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: colors.background },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  brand: { color: colors.text, fontSize: 22, fontWeight: '900', letterSpacing: -0.7 },
  accountRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, maxWidth: '48%' },
  accountCopy: { alignItems: 'flex-end', flexShrink: 1 },
  accountName: { color: colors.text, fontSize: 13, fontWeight: '700' },
  accountRole: { color: colors.accent, fontSize: 10, fontWeight: '900' },
  logoutButton: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surface },
  content: { flex: 1 },
  tabBar: { minHeight: 70, paddingBottom: Platform.OS === 'android' ? 7 : 0, flexDirection: 'row', borderTopColor: colors.border, borderTopWidth: 1, backgroundColor: colors.surface },
  tab: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 4, paddingHorizontal: 2 },
  tabLabel: { color: colors.muted, fontSize: 10, fontWeight: '700' },
  tabLabelActive: { color: colors.accent },
});
