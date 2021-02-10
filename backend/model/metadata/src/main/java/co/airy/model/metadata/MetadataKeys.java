package co.airy.model.metadata;

/**
 * JSON dot notation keys for pre-defined metadata
 */
public class MetadataKeys {
    public static String USER_DATA = "user_data";
    public static class ConversationKeys {
        public static final String TAGS = "tags";

        public static class Contact {
            public static final String FIRST_NAME = "contact.first_name";
            public static final String LAST_NAME = "contact.last_name";
            public static final String AVATAR_URL = "contact.avatar_url";
            public static final String FETCH_STATE = "contact.fetch_state";
        }

        public enum ContactFetchState {
            ok("ok"),
            failed("failed");

            private final String state;

            ContactFetchState(final String state) {
                this.state = state;
            }

            @Override
            public String toString() {
                return state;
            }
        }
    }

    public static class ChannelKeys {
        public static final String NAME = "name";
        public static final String IMAGE_URL = "image_url";
    }
}

