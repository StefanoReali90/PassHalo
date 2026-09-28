import { useEffect, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text } from 'react-native';
import { api, type BrevoStatus } from '../api';
import { Button, Card, Field, Notice, PageHeader } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { colors, spacing } from '../theme';

export function SettingsScreen() {
  const { apiBaseUrl, saveApiBaseUrl, user } = useAuth();
  const [value, setValue] = useState(apiBaseUrl);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [brevoStatus, setBrevoStatus] = useState<BrevoStatus | null>(null);
  const [brevoKey, setBrevoKey] = useState('');
  const [brevoListId, setBrevoListId] = useState('');
  const [brevoBusy, setBrevoBusy] = useState(false);
  const [brevoError, setBrevoError] = useState('');
  const [brevoMessage, setBrevoMessage] = useState('');

  useEffect(() => setValue(apiBaseUrl), [apiBaseUrl]);

  useEffect(() => {
    if (user?.role !== 'ADMIN') return;
    let active = true;
    const refresh = () => api.brevoStatus()
      .then(status => { if (active) setBrevoStatus(status); })
      .catch(requestError => {
        if (active) setBrevoError(requestError instanceof Error ? requestError.message : 'Stato Brevo non disponibile.');
      });
    refresh();
    const interval = setInterval(refresh, 15_000);
    return () => { active = false; clearInterval(interval); };
  }, [user?.role]);

  const saveBrevo = async () => {
    setBrevoBusy(true);
    setBrevoError('');
    setBrevoMessage('');
    try {
      const status = brevoStatus?.connected
        ? await api.rotateBrevoKey(brevoKey)
        : await api.connectBrevo(brevoKey, Number(brevoListId));
      setBrevoStatus(status);
      setBrevoKey('');
      setBrevoMessage(brevoStatus?.connected ? 'Chiave aggiornata. La sincronizzazione riprenderà automaticamente.' : 'Account Brevo collegato.');
    } catch (requestError) {
      setBrevoError(requestError instanceof Error ? requestError.message : 'Configurazione Brevo non riuscita.');
    } finally {
      setBrevoBusy(false);
    }
  };

  const removeBrevo = () => Alert.alert(
    'Scollegare Brevo?',
    'I tuoi contatti attivi saranno rimossi dalla lista configurata.',
    [
      { text: 'Annulla', style: 'cancel' },
      { text: 'Scollega', style: 'destructive', onPress: async () => {
        setBrevoBusy(true);
        setBrevoError('');
        try {
          await api.disconnectBrevo();
          setBrevoStatus({ connected: false, listId: null, pendingContacts: 0 });
          setBrevoMessage('Account Brevo scollegato.');
        } catch (requestError) {
          setBrevoError(requestError instanceof Error ? requestError.message : 'Scollegamento non riuscito.');
        } finally {
          setBrevoBusy(false);
        }
      } },
    ],
  );

  const saveAndTest = async () => {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const normalized = await saveApiBaseUrl(value);
      const events = await api.events();
      setValue(normalized);
      setMessage(`Connessione riuscita. ${events.length} eventi ricevuti.`);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Configurazione non valida.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.page} keyboardShouldPersistTaps="handled">
      <PageHeader eyebrow="Connessione" title="Server PassHalo." description="Puoi cambiare server senza reinstallare o ricompilare l’app." />
      {message ? <Notice tone="success">{message}</Notice> : null}
      {error ? <Notice tone="error">{error}</Notice> : null}
      <Card>
        <Field
          label="URL base delle API"
          value={value}
          onChangeText={setValue}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          placeholder="https://passhalo.it/api"
        />
        <Button label="Salva e verifica" onPress={saveAndTest} busy={busy} disabled={!value.trim()} />
      </Card>
      <Card>
        <Text style={styles.title}>Indirizzo pubblico</Text>
        <Text style={styles.copy}>Usa l’indirizzo HTTPS delle API, con <Text style={styles.code}>/api</Text> alla fine se il sito e le API condividono il dominio.</Text>
        <Text style={styles.example}>https://passhalo.it/api</Text>
        <Text style={styles.copy}>Cambiare server disconnette l’utente per evitare di riutilizzare un token sul backend sbagliato.</Text>
      </Card>
      {user?.role === 'ADMIN' ? <Card>
        <Text style={styles.title}>Il tuo account Brevo</Text>
        <Text style={styles.copy}>Collega una lista dedicata alle campagne dei tuoi eventi. I contatti con consenso valido saranno sincronizzati automaticamente.</Text>
        {brevoError ? <Notice tone="error">{brevoError}</Notice> : null}
        {brevoMessage ? <Notice tone="success">{brevoMessage}</Notice> : null}
        {brevoStatus?.connected ? <Notice tone="success">Lista {brevoStatus.listId} collegata. Contatti in attesa: {brevoStatus.pendingContacts}.</Notice> : null}
        {brevoStatus && !brevoStatus.connected ? <Field
          label="ID lista Brevo"
          value={brevoListId}
          onChangeText={setBrevoListId}
          keyboardType="number-pad"
          placeholder="42"
        /> : null}
        {brevoStatus ? <>
          <Field
            label={brevoStatus.connected ? 'Nuova chiave API dello stesso account' : 'Chiave API Brevo'}
            value={brevoKey}
            onChangeText={setBrevoKey}
            autoCapitalize="none"
            autoCorrect={false}
            secureTextEntry
            placeholder="Chiave API"
          />
          <Button label={brevoStatus.connected ? 'Sostituisci chiave API' : 'Collega Brevo'}
            onPress={saveBrevo} busy={brevoBusy}
            disabled={!brevoKey.trim() || (!brevoStatus.connected && !/^[1-9]\d*$/.test(brevoListId))} />
          {brevoStatus.connected ? <Button label="Scollega Brevo" variant="secondary" onPress={removeBrevo}
            disabled={brevoBusy || brevoStatus.pendingContacts > 0} /> : null}
        </> : null}
      </Card> : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  page: { padding: spacing.lg, paddingBottom: 120, gap: spacing.lg },
  title: { color: colors.text, fontSize: 18, fontWeight: '800' },
  copy: { color: colors.muted, fontSize: 14, lineHeight: 21 },
  code: { color: colors.accent, fontWeight: '800' },
  example: { color: colors.text, fontSize: 13, backgroundColor: colors.background, padding: spacing.md, borderRadius: 12 },
});
