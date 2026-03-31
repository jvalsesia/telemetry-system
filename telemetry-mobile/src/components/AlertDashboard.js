import React, { useEffect, useState, useRef } from 'react';
import { View, Text, StyleSheet, FlatList, Animated, Dimensions, Platform } from 'react-native';
import EventSource from 'react-native-sse';

const { width } = Dimensions.get('window');

// Automatically configure the BFF URL based on the platform for local dev
const BFF_URL = Platform.select({
  android: 'http://10.0.2.2:8084/stream/alerts',
  default: 'http://localhost:8084/stream/alerts',
});

const AlertItem = ({ alert, index }) => {
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(20)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 400,
        delay: Math.min(index * 100, 500),
        useNativeDriver: true,
      }),
      Animated.timing(slideAnim, {
        toValue: 0,
        duration: 400,
        delay: Math.min(index * 100, 500),
        useNativeDriver: true,
      }),
    ]).start();
  }, []);

  const getSeverityColor = (severity) => {
    if (severity && severity.toLowerCase() === 'critical') return '#ef4444'; // Red
    if (severity && severity.toLowerCase() === 'warning') return '#f59e0b'; // Amber
    return '#3b82f6'; // Blue
  };

  const isKeepAlive = alert.deviceId === 'keep-alive';
  if (isKeepAlive) return null; // We don't want to visually list keep-alive pings

  return (
    <Animated.View style={[
      styles.card,
      { opacity: fadeAnim, transform: [{ translateY: slideAnim }] }
    ]}>
      <View style={[styles.indicator, { backgroundColor: getSeverityColor(alert.reason) }]} />
      <View style={styles.content}>
        <Text style={styles.title}>Device {alert.deviceId}</Text>
        <Text style={styles.reason}>{alert.reason}</Text>
      </View>
      <View style={styles.metrics}>
        <Text style={styles.metricText}>HR: {alert.hr}</Text>
        <Text style={styles.metricText}>SpO2: {alert.spo2}%</Text>
      </View>
    </Animated.View>
  );
};

export default function AlertDashboard() {
  const [alerts, setAlerts] = useState([]);
  const [status, setStatus] = useState('Connecting...');

  useEffect(() => {
    console.log(`Connecting to SSE at ${BFF_URL}`);
    const es = new EventSource(BFF_URL);

    es.addEventListener('open', (event) => {
      console.log('SSE connection opened');
      setStatus('Connected');
    });

    es.addEventListener('alert', (event) => {
      try {
        const payload = JSON.parse(event.data);
        // Only keep the latest 50 alerts to avoid memory bloat
        setAlerts((prev) => [payload, ...prev].slice(0, 50));
      } catch (e) {
        console.error('Failed to parse alert', e);
      }
    });

    es.addEventListener('ping', (event) => {
       console.log('keep-alive ping received');
    });

    es.addEventListener('error', (event) => {
      console.log('SSE error:', event.type, event.message);
      setStatus('Disconnected - Retrying...');
    });

    return () => {
      es.close();
    };
  }, []);

  const renderHeader = () => (
    <View style={styles.header}>
      <Text style={styles.headerTitle}>Telemetry Hub</Text>
      <View style={styles.statusBadge}>
        <View style={[styles.statusDot, { backgroundColor: status === 'Connected' ? '#10b981' : '#f59e0b' }]} />
        <Text style={styles.statusText}>{status}</Text>
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      {renderHeader()}
      {alerts.length === 0 && status === 'Connected' ? (
         <View style={styles.emptyState}>
            <Text style={styles.emptyStateText}>No active alerts.</Text>
         </View>
      ) : (
        <FlatList
          data={alerts}
          keyExtractor={(_, index) => index.toString()}
          renderItem={({ item, index }) => <AlertItem alert={item} index={index} />}
          contentContainerStyle={styles.listContainer}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0a0a0a',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 40,
    paddingBottom: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#1f1f1f',
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: '800',
    color: '#ffffff',
    letterSpacing: 0.5,
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1f1f1f',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  statusText: {
    color: '#a3a3a3',
    fontSize: 12,
    fontWeight: '600',
  },
  listContainer: {
    padding: 16,
    gap: 12,
  },
  card: {
    flexDirection: 'row',
    backgroundColor: '#171717',
    borderRadius: 16,
    overflow: 'hidden',
    alignItems: 'center',
    elevation: 4, // for android shadow
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    marginBottom: 12, // fallback for gap
  },
  indicator: {
    width: 6,
    height: '100%',
    minHeight: 80, // force height
  },
  content: {
    flex: 1,
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
    color: '#ffffff',
    marginBottom: 4,
  },
  reason: {
    fontSize: 14,
    color: '#a3a3a3',
    textTransform: 'capitalize',
  },
  metrics: {
    padding: 16,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  metricText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#e5e5e5',
    marginBottom: 2,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyStateText: {
    color: '#525252',
    fontSize: 16,
  }
});
