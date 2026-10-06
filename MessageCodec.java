import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Converts {@link Message} objects to/from the wire string that is
 * handed to {@code PeerConnectionManager.sendText()}.
 *
 * Wire format (pipe-delimited, fields 3-8 are Base64-URL encoded):
 *
 *   MESHMSG|1|<TYPE>|<messageId-b64>|<senderId-b64>|<receiverId-b64>|<timestamp>|<payload-b64>
 *
 * Encoding rules
 * ──────────────
 * • Only the unambiguous prefix (MESHMSG), the version token (1) and the
 *   message-type token remain as plain ASCII.
 * • Every other field that could contain arbitrary characters is encoded
 *   with Base64-URL (no padding) so pipes, spaces, Unicode, etc. are all
 *   safe.
 * • The timestamp is a plain decimal long and never needs encoding.
 *
 * The networking layer (PeerConnectionManager) never sees or interprets
 * this format — it simply transports the string as a TEXT frame.
 */
public final class MessageCodec {

    // ── Wire-format constants ────────────────────────────────────────────────

    /** Prefix that every MeshLink application message must start with. */
    private static final String PREFIX  = "MESHMSG";

    /** Protocol version embedded in every frame. */
    private static final String VERSION = "1";

    /** Delimiter used between fields. */
    private static final char   SEP     = '|';

    /** Expected number of '|'-separated fields in a valid encoded message. */
    private static final int    FIELD_COUNT = 8;

    // ── Codec API ────────────────────────────────────────────────────────────

    /**
     * Encodes a {@link Message} into a single string suitable for transport
     * via {@code PeerConnectionManager.sendText()}.
     *
     * @param message the message to encode (must not be {@code null})
     * @return the encoded wire string
     */
    public static String encode(Message message) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();

        return PREFIX + SEP
                + VERSION + SEP
                + message.getType().name() + SEP
                + enc.encodeToString(utf8(message.getMessageId()))  + SEP
                + enc.encodeToString(utf8(message.getSenderId()))   + SEP
                + enc.encodeToString(utf8(message.getReceiverId())) + SEP
                + message.getTimestamp() + SEP
                + enc.encodeToString(utf8(message.getPayload()));
    }

    /**
     * Decodes a wire string back into a {@link Message}.
     *
     * Returns {@code null} (does NOT throw) when the string is not a
     * valid MeshLink application message — the caller can then treat it
     * as a raw/legacy message instead of crashing.
     *
     * @param wire the string received from {@code PeerConnectionManager.onText()}
     * @return the decoded {@link Message}, or {@code null} if decoding fails
     */
    public static Message decode(String wire) {
        if (wire == null || wire.isEmpty()) {
            return null;
        }

        // Fast-fail before splitting: must start with the known prefix
        if (!wire.startsWith(PREFIX + SEP)) {
            return null;
        }

        String[] parts = wire.split("\\|", FIELD_COUNT);

        if (parts.length != FIELD_COUNT) {
            return null;
        }

        // parts[0] = PREFIX  (already checked)
        // parts[1] = version
        if (!VERSION.equals(parts[1])) {
            System.err.println("[MessageCodec] Unsupported version: " + parts[1]);
            return null;
        }

        // parts[2] = message type
        MessageType type;
        try {
            type = MessageType.valueOf(parts[2]);
        } catch (IllegalArgumentException e) {
            System.err.println("[MessageCodec] Unknown message type: " + parts[2]);
            return null;
        }

        Base64.Decoder dec = Base64.getUrlDecoder();

        try {
            String messageId  = fromUtf8(dec.decode(parts[3]));
            String senderId   = fromUtf8(dec.decode(parts[4]));
            String receiverId = fromUtf8(dec.decode(parts[5]));
            long   timestamp  = Long.parseLong(parts[6]);
            String payload    = fromUtf8(dec.decode(parts[7]));

            return new Message(messageId, type, senderId, receiverId, timestamp, payload);

        } catch (Exception e) {
            System.err.println("[MessageCodec] Decode error: " + e.getMessage());
            return null;
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String fromUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // Prevent instantiation — this is a pure static utility class.
    private MessageCodec() {}
}
