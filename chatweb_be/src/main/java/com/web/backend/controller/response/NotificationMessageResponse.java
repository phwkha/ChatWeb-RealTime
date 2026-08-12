package com.web.backend.controller.response;

import com.web.backend.common.NotificationsType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessageResponse {

    private NotificationsType type;
    private String relatedUsername;

}
