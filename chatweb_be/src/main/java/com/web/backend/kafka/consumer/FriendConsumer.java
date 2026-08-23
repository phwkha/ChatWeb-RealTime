package com.web.backend.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.stereotype.Component;

import com.web.backend.common.NotificationsType;
import com.web.backend.config.localresolverconfig.Translator;
import com.web.backend.controller.response.NotificationResponse;
import com.web.backend.kafka.payload.FriendPayload;
import com.web.backend.service.WebSocketRoutingService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j(topic = "FRIEND-KAFKA-CONSUMER")
public class FriendConsumer {

    private final WebSocketRoutingService webSocketRoutingService;

    private static final String QUEUE_NOTIFICATIONS_STRING = "/queue/notifications";

    private static final String SYS_MSG_NEW_FRIEND_INVITE_STRING = "sys.msg.new_friend_invite";
    private static final String SUCCESS_FRIEND_INVITE_SENT_STRING = "success.friend.invite_sent";
    private static final String SUCCESS_FRIEND_ACCEPTED_STRING = "success.friend.accepted";
    private static final String SUCCESS_FRIEND_YOU_ACCEPTED_STRING = "success.friend.you_accepted";
    private static final String SUCCESS_FRIEND_UNFRIENDED_STRING = "success.friend.unfriended";
    private static final String SUCCESS_FRIEND_INVITE_RETRACTED_STRING = "success.friend.invite_retracted";
    private static final String SUCCESS_FRIEND_INVITE_DECLINED_STRING = "success.friend.invite_declined";
    private static final String SYS_MSG_USER_ONLINE_STRING = "sys.msg.user_online";
    private static final String SYS_MSG_USER_OFFLINE_STRING = "sys.msg.user_offline";
    private static final String EMPTY_STRING = "";

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 500), sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, dltStrategy = DltStrategy.NO_DLT, autoCreateTopics = "true")
    @KafkaListener(topics = "${spring.kafka.topic.friend.friend-topic}", groupId = "${spring.kafka.topic.friend.friend-group-id}", containerFactory = "jsonKafkaListenerContainerFactory")
    public void listenFriendNotifications(FriendPayload friendEvent) {
        if (friendEvent == null) {
            return;
        }

        String recipient = friendEvent.recipientUsername();
        List<String> recipients = friendEvent.recipientUsernames();
        String sender = friendEvent.senderUsername();
        log.debug("Consumed friend notification event: sender='{}', recipient='{}', type='{}'", sender, recipient,
                friendEvent.recipientType());

        try {
            NotificationResponse<?> recipientResp = buildResponse(friendEvent.recipientType(),
                    friendEvent.senderDisplayName());
            NotificationResponse<?> senderResp = buildResponse(friendEvent.senderType(),
                    friendEvent.recipientDisplayName());

            if (recipients != null && !recipients.isEmpty() && recipientResp != null) {
                for (String r : recipients) {
                    webSocketRoutingService.routeMessage(r, QUEUE_NOTIFICATIONS_STRING, recipientResp);
                }
            } else if (recipient != null && recipientResp != null) {
                webSocketRoutingService.routeMessage(recipient, QUEUE_NOTIFICATIONS_STRING, recipientResp);
            }

            if (sender != null && senderResp != null) {
                webSocketRoutingService.routeMessage(sender, QUEUE_NOTIFICATIONS_STRING, senderResp);
            }
        } catch (Exception e) {
            log.error("Failed to route WebSocket friend notification: sender='{}', recipient='{}'", sender, recipient,
                    e);
            throw new RuntimeException(e);
        }
    }

    private NotificationResponse<?> buildResponse(NotificationsType type,
            String relatedUsername) {
        if (type == null) {
            return null;
        }

        String translationKey;
        switch (type) {
            case FRIEND_REQUEST:
                translationKey = SYS_MSG_NEW_FRIEND_INVITE_STRING;
                break;
            case REQUEST_SENT_SUCCESS:
                translationKey = SUCCESS_FRIEND_INVITE_SENT_STRING;
                break;
            case FRIEND_ACCEPTED:
                translationKey = SUCCESS_FRIEND_ACCEPTED_STRING;
                break;
            case YOU_ACCEPTED:
                translationKey = SUCCESS_FRIEND_YOU_ACCEPTED_STRING;
                break;
            case UNFRIENDED:
                translationKey = SUCCESS_FRIEND_UNFRIENDED_STRING;
                break;
            case REQUEST_CANCELLED:
                translationKey = SUCCESS_FRIEND_INVITE_RETRACTED_STRING;
                break;
            case REQUEST_REJECTED:
                translationKey = SUCCESS_FRIEND_INVITE_DECLINED_STRING;
                break;
            case USER_ONLINE:
                translationKey = SYS_MSG_USER_ONLINE_STRING;
                break;
            case USER_OFFLINE:
                translationKey = SYS_MSG_USER_OFFLINE_STRING;
                break;
            default:
                translationKey = EMPTY_STRING;
        }

        return NotificationResponse.notificationData(type, relatedUsername, Translator.tolocale(translationKey));
    }

}
