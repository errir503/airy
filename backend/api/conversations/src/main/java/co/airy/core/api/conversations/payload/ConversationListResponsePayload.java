package co.airy.core.api.conversations.payload;

import co.airy.payload.response.ConversationResponsePayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationListResponsePayload {
    private List<ConversationResponsePayload> data;
    private ResponseMetadata responseMetadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMetadata {
        private String previousCursor;
        private String nextCursor;
        private long filteredTotal;
        private long total; //total conversation count
    }
}
