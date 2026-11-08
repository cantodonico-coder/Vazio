package com.nicoshake.shortsautonext

import android.service.notification.NotificationListenerService

/**
 * Não lê nem guarda notificações. Existe só porque o Android exige
 * "acesso a notificações" para ler a posição/duração do vídeo que o
 * YouTube está tocando (MediaSession).
 */
class MediaListener : NotificationListenerService()
