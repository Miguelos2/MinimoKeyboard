package com.miguel.keyboard

import android.service.notification.NotificationListenerService

/**
 * Empty notification listener used only as the authorized component that lets
 * MediaSessionManager expose active media sessions to the keyboard.
 */
class MusicSessionNotificationListenerService : NotificationListenerService()
