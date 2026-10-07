package top.productivitytools.spendings.webapi.dto;

import java.time.OffsetDateTime;

public record RawEmailRequest(
        String messageId,
        String threadId,
        String source,
        String subject,
        String attachmentName,
        OffsetDateTime emailDate,
        String rawHtml
) {
}
