/**
 * Push Notification Service for Expo Apps
 * 
 * This service handles push notification registration and handling for Expo apps.
 * Key point: Uses getDevicePushTokenAsync() to get native tokens (FCM for Android, APNs for iOS)
 * 
 * @version 1.0.0
 */

import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import Constants from 'expo-constants';
import * as Device from 'expo-device';

// Types
export interface PushTokenData {
  token: string;
  platform: 'ios' | 'android';
  userId: string;
}

export interface NotificationContent {
  title: string;
  body: string;
  data?: Record<string, any>;
  badge?: number;
  sound?: string;
}

export interface NotificationResponse {
  notification: Notifications.Notification;
  actionIdentifier: string;
}

// Configuration
const config = {
  API_BASE_URL: process.env.EXPO_PUBLIC_API_URL || 'https://your-api.com',
  ENDPOINTS: {
    REGISTER_TOKEN: '/api/push/token',
    UNREGISTER_TOKEN: '/api/push/token',
    SEND_NOTIFICATION: '/api/push/send'
  }
};

/**
 * Main Push Notification Service Class
 */
export class PushNotificationService {
  private static instance: PushNotificationService;
  private notificationListener: any;
  private responseListener: any;
  private token: string | null = null;
  
  private constructor() {
    this.setupNotificationHandler();
  }
  
  /**
   * Get singleton instance
   */
  public static getInstance(): PushNotificationService {
    if (!PushNotificationService.instance) {
      PushNotificationService.instance = new PushNotificationService();
    }
    return PushNotificationService.instance;
  }
  
  /**
   * Setup notification handler (how notifications behave when received)
   */
  private setupNotificationHandler(): void {
    Notifications.setNotificationHandler({
      handleNotification: async () => ({
        shouldShowAlert: true,
        shouldPlaySound: true,
        shouldSetBadge: true,
      }),
    });
  }
  
  /**
   * Register for push notifications and get device token
   * 
   * IMPORTANT: Returns FCM token for Android, APNs token for iOS
   */
  public async registerForPushNotifications(userId: string): Promise<string | null> {
    try {
      // Check if running on physical device
      if (!Device.isDevice) {
        console.log('Push Notifications only work on physical devices');
        return null;
      }
      
      // Check current permission status
      const { status: existingStatus } = await Notifications.getPermissionsAsync();
      let finalStatus = existingStatus;
      
      // Request permission if not granted
      if (existingStatus !== 'granted') {
        const { status } = await Notifications.requestPermissionsAsync();
        finalStatus = status;
      }
      
      if (finalStatus !== 'granted') {
        console.log('Push notification permissions not granted');
        return null;
      }
      
      // Get device push token (NOT Expo Push Token!)
      // This returns FCM token for Android, APNs token for iOS
      const tokenData = await Notifications.getDevicePushTokenAsync({
        // For iOS development, you might need to specify the projectId
        // projectId: Constants.expoConfig?.extra?.eas?.projectId,
      });
      
      this.token = tokenData.data;
      
      console.log(`Device Push Token obtained: ${this.token}`);
      console.log(`Platform: ${Platform.OS}`);
      console.log(`Token Type: ${tokenData.type}`); // Should be 'ios' or 'android'
      
      // Setup Android channel (required for Android 8+)
      if (Platform.OS === 'android') {
        await this.setupAndroidChannel();
      }
      
      // Register token with backend
      await this.sendTokenToServer({
        token: this.token,
        platform: Platform.OS as 'ios' | 'android',
        userId
      });
      
      // Setup listeners
      this.setupNotificationListeners();
      
      return this.token;
      
    } catch (error) {
      console.error('Failed to register for push notifications:', error);
      return null;
    }
  }
  
  /**
   * Setup Android notification channel
   */
  private async setupAndroidChannel(): Promise<void> {
    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'Default',
        importance: Notifications.AndroidImportance.MAX,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#FF231F7C',
        sound: 'default',
      });
    }
  }
  
  /**
   * Send token to backend server
   */
  private async sendTokenToServer(data: PushTokenData): Promise<void> {
    try {
      const response = await fetch(
        `${config.API_BASE_URL}${config.ENDPOINTS.REGISTER_TOKEN}`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            // Add authentication headers if needed
            // 'Authorization': `Bearer ${getAuthToken()}`,
          },
          body: JSON.stringify(data),
        }
      );
      
      if (!response.ok) {
        throw new Error(`Server responded with ${response.status}`);
      }
      
      const result = await response.json();
      console.log('Token registered successfully:', result);
      
    } catch (error) {
      console.error('Failed to send token to server:', error);
      // You might want to retry or queue this operation
      throw error;
    }
  }
  
  /**
   * Setup notification listeners
   */
  private setupNotificationListeners(): void {
    // Cleanup existing listeners
    this.removeNotificationListeners();
    
    // Listener for notifications received while app is in foreground
    this.notificationListener = Notifications.addNotificationReceivedListener(
      (notification) => {
        console.log('Notification received:', notification);
        this.handleNotificationReceived(notification);
      }
    );
    
    // Listener for when user interacts with notification
    this.responseListener = Notifications.addNotificationResponseReceivedListener(
      (response) => {
        console.log('Notification clicked:', response);
        this.handleNotificationResponse(response);
      }
    );
  }
  
  /**
   * Handle notification received (while app is in foreground)
   */
  private handleNotificationReceived(notification: Notifications.Notification): void {
    const { title, body, data } = notification.request.content;
    
    console.log('Notification Content:', {
      title,
      body,
      data
    });
    
    // You can update UI or state here
    // For example, update badge count, refresh data, etc.
    if (data?.type === 'chat') {
      // Handle chat notification
      this.handleChatNotification(data);
    }
  }
  
  /**
   * Handle notification response (when user taps on notification)
   */
  private handleNotificationResponse(response: NotificationResponse): void {
    const { data } = response.notification.request.content;
    
    console.log('User interacted with notification:', data);
    
    // Navigate based on notification data
    if (data?.type === 'chat' && data?.chatId) {
      // Navigate to chat screen
      this.navigateToChat(data.chatId);
    } else if (data?.type === 'order' && data?.orderId) {
      // Navigate to order details
      this.navigateToOrder(data.orderId);
    }
    
    // Clear badge if needed
    Notifications.setBadgeCountAsync(0);
  }
  
  /**
   * Handle chat notification
   */
  private handleChatNotification(data: any): void {
    // Implement your chat notification logic
    console.log('New chat message:', data);
    // Update chat state, show in-app notification, etc.
  }
  
  /**
   * Navigate to chat screen
   */
  private navigateToChat(chatId: string): void {
    // Implement navigation logic
    // This depends on your navigation setup (React Navigation, etc.)
    console.log('Navigate to chat:', chatId);
  }
  
  /**
   * Navigate to order screen
   */
  private navigateToOrder(orderId: string): void {
    // Implement navigation logic
    console.log('Navigate to order:', orderId);
  }
  
  /**
   * Unregister token (for logout)
   */
  public async unregisterToken(userId: string): Promise<void> {
    try {
      await fetch(
        `${config.API_BASE_URL}${config.ENDPOINTS.UNREGISTER_TOKEN}/${userId}`,
        {
          method: 'DELETE',
          headers: {
            // Add authentication headers if needed
            // 'Authorization': `Bearer ${getAuthToken()}`,
          },
        }
      );
      
      // Cleanup listeners
      this.removeNotificationListeners();
      
      // Clear badge
      await Notifications.setBadgeCountAsync(0);
      
      console.log('Token unregistered successfully');
      
    } catch (error) {
      console.error('Failed to unregister token:', error);
    }
  }
  
  /**
   * Remove notification listeners
   */
  private removeNotificationListeners(): void {
    if (this.notificationListener) {
      Notifications.removeNotificationSubscription(this.notificationListener);
      this.notificationListener = null;
    }
    
    if (this.responseListener) {
      Notifications.removeNotificationSubscription(this.responseListener);
      this.responseListener = null;
    }
  }
  
  /**
   * Schedule local notification (for testing)
   */
  public async scheduleLocalNotification(
    content: NotificationContent,
    trigger: Notifications.NotificationTriggerInput
  ): Promise<string> {
    return await Notifications.scheduleNotificationAsync({
      content: {
        title: content.title,
        body: content.body,
        data: content.data,
        badge: content.badge,
        sound: content.sound || 'default',
      },
      trigger,
    });
  }
  
  /**
   * Cancel scheduled notification
   */
  public async cancelScheduledNotification(notificationId: string): Promise<void> {
    await Notifications.cancelScheduledNotificationAsync(notificationId);
  }
  
  /**
   * Get all scheduled notifications
   */
  public async getScheduledNotifications(): Promise<Notifications.NotificationRequest[]> {
    return await Notifications.getAllScheduledNotificationsAsync();
  }
  
  /**
   * Cancel all scheduled notifications
   */
  public async cancelAllScheduledNotifications(): Promise<void> {
    await Notifications.cancelAllScheduledNotificationsAsync();
  }
  
  /**
   * Get current badge count (iOS)
   */
  public async getBadgeCount(): Promise<number> {
    return await Notifications.getBadgeCountAsync();
  }
  
  /**
   * Set badge count (iOS)
   */
  public async setBadgeCount(count: number): Promise<boolean> {
    return await Notifications.setBadgeCountAsync(count);
  }
  
  /**
   * Get current token
   */
  public getToken(): string | null {
    return this.token;
  }
  
  /**
   * Check if notifications are enabled
   */
  public async areNotificationsEnabled(): Promise<boolean> {
    const { status } = await Notifications.getPermissionsAsync();
    return status === 'granted';
  }
  
  /**
   * Request notification permissions
   */
  public async requestPermissions(): Promise<boolean> {
    const { status } = await Notifications.requestPermissionsAsync();
    return status === 'granted';
  }
}

/**
 * Hook for using push notifications in React components
 */
export function usePushNotifications(userId: string) {
  const [expoPushToken, setExpoPushToken] = React.useState<string | null>(null);
  const [notification, setNotification] = React.useState<Notifications.Notification | null>(null);
  const notificationListener = React.useRef<any>();
  const responseListener = React.useRef<any>();
  
  React.useEffect(() => {
    const service = PushNotificationService.getInstance();
    
    // Register for push notifications
    service.registerForPushNotifications(userId).then(token => {
      setExpoPushToken(token);
    });
    
    // Setup listeners
    notificationListener.current = Notifications.addNotificationReceivedListener(notification => {
      setNotification(notification);
    });
    
    responseListener.current = Notifications.addNotificationResponseReceivedListener(response => {
      console.log('Notification response:', response);
      // Handle navigation here
    });
    
    return () => {
      if (notificationListener.current) {
        Notifications.removeNotificationSubscription(notificationListener.current);
      }
      if (responseListener.current) {
        Notifications.removeNotificationSubscription(responseListener.current);
      }
    };
  }, [userId]);
  
  return {
    expoPushToken,
    notification,
  };
}

// Export singleton instance
export default PushNotificationService.getInstance();
