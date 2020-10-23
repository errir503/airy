package co.airy.core.api.communication;

import co.airy.avro.communication.Channel;
import co.airy.avro.communication.ChannelConnectionState;
import co.airy.core.api.communication.payload.MessageUpsertPayload;
import co.airy.core.api.communication.payload.UnreadCountPayload;
import co.airy.core.api.communication.util.ConversationGenerator;
import co.airy.kafka.schema.application.ApplicationCommunicationChannels;
import co.airy.kafka.schema.application.ApplicationCommunicationMessages;
import co.airy.kafka.schema.application.ApplicationCommunicationMetadata;
import co.airy.kafka.schema.application.ApplicationCommunicationReadReceipts;
import co.airy.kafka.test.TestHelper;
import co.airy.kafka.test.junit.SharedKafkaTestResource;
import co.airy.payload.response.ChannelPayload;
import co.airy.spring.core.AirySpringBootApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static co.airy.core.api.communication.WebSocketController.QUEUE_CHANNEL_CONNECTED;
import static co.airy.core.api.communication.WebSocketController.QUEUE_MESSAGE;
import static co.airy.core.api.communication.WebSocketController.QUEUE_UNREAD_COUNT;
import static co.airy.core.api.communication.util.ConversationGenerator.getConversationRecords;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("kafka-integration")
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {AirySpringBootApplication.class})
@AutoConfigureMockMvc
public class WebSocketControllerTest {
    @RegisterExtension
    public static final SharedKafkaTestResource sharedKafkaTestResource = new SharedKafkaTestResource();

    private static final ApplicationCommunicationChannels applicationCommunicationChannels = new ApplicationCommunicationChannels();
    private static final ApplicationCommunicationMessages applicationCommunicationMessages = new ApplicationCommunicationMessages();
    private static final ApplicationCommunicationMetadata applicationCommunicationMetadata = new ApplicationCommunicationMetadata();
    private static final ApplicationCommunicationReadReceipts applicationCommunicationReadReceipts = new ApplicationCommunicationReadReceipts();

    private static TestHelper testHelper;

    private static boolean testDataInitialized = false;

    private static final String conversationId = "conversation-id";

    final Channel channel = Channel.newBuilder()
            .setConnectionState(ChannelConnectionState.CONNECTED)
            .setId("facebook-channel-id")
            .setName("channel-name")
            .setSource("facebook")
            .setSourceChannelId("ps-id")
            .setToken("AWESOME TOKEN")
            .build();

    private final List<ConversationGenerator.CreateConversation> conversations = List.of(
            ConversationGenerator.CreateConversation.builder()
                    .conversationId(conversationId)
                    .messageCount(1L)
                    .channel(channel)
                    .build());

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private Stores store;

    @BeforeAll
    static void beforeAll() throws Exception {
        testHelper = new TestHelper(sharedKafkaTestResource,
                applicationCommunicationMetadata,
                applicationCommunicationMessages,
                applicationCommunicationChannels,
                applicationCommunicationReadReceipts
        );
        testHelper.beforeAll();
    }

    @AfterAll
    static void afterAll() throws Exception {
        testHelper.afterAll();
    }

    @BeforeEach
    void init() throws Exception {
        if (testDataInitialized) {
            return;
        }

        testHelper.waitForCondition(
                () -> mvc.perform(get("/health")).andExpect(status().isOk()),
                "Application is not healthy"
        );

        testDataInitialized = true;
    }

    @Test
    void sendsToWebsocket() throws Exception {
        final CompletableFuture<MessageUpsertPayload> messageFuture = subscribe(port, MessageUpsertPayload.class, QUEUE_MESSAGE);
        final CompletableFuture<ChannelPayload> channelFuture = subscribe(port, ChannelPayload.class, QUEUE_CHANNEL_CONNECTED);
        final CompletableFuture<UnreadCountPayload> unreadFuture = subscribe(port, UnreadCountPayload.class, QUEUE_UNREAD_COUNT);

        testHelper.produceRecord(new ProducerRecord<>(applicationCommunicationChannels.name(), channel.getId(), channel));
        testHelper.produceRecords(getConversationRecords(conversations));

        final MessageUpsertPayload recMessage = messageFuture.get(30, TimeUnit.SECONDS);

        assertNotNull(recMessage);
        assertThat(recMessage.getConversationId(), is(conversationId));

        final ChannelPayload receivedChannel = channelFuture.get(30, TimeUnit.SECONDS);

        assertNotNull(receivedChannel);

        assertThat(receivedChannel.getId(), is(channel.getId()));

        final UnreadCountPayload receivedUnreadCount = unreadFuture.get(30, TimeUnit.SECONDS);

        assertNotNull(receivedUnreadCount);
        assertThat(receivedUnreadCount.getUnreadMessageCount(), is(1));

    }

    private static StompSession connectToWs(int port) throws ExecutionException, InterruptedException {
        final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        ObjectMapper objectMapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        messageConverter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(messageConverter);

        StompHeaders connectHeaders = new StompHeaders();
        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();

        return stompClient.connect("ws://localhost:" + port + "/ws.communication", httpHeaders, connectHeaders, new StompSessionHandlerAdapter() {
        }).get();
    }

    public static <T> CompletableFuture<T> subscribe(int port, Class<T> payloadType, String topic) throws ExecutionException, InterruptedException {
        final StompSession stompSession = connectToWs(port);

        final CompletableFuture<T> completableFuture = new CompletableFuture<>();

        stompSession.subscribe(topic, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                completableFuture.complete((T) payload);
            }
        });

        return completableFuture;
    }
}