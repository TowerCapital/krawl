/**
 * Application service for the MeshLink message layer.
 *
 * Responsibilities
 * ────────────────
 * • Create typed {@link Message} objects from user intent.
 * • Encode a {@link Message} for transport via the networking layer.
 * • Decode an incoming wire string back into a {@link Message}.
 *
 * What this class does NOT do
 * ────────────────────────────
 * • It does NOT open sockets.
 * • It does NOT know about TCP frames or UDP discovery.
 * • It does NOT persist messages.
 *
 * The networking layer ({@code PeerConnectionManager}) remains a
 * pure transport: it hands encoded strings across the wire without
 * interpreting them.
 */
public final class MessageService {

    // ── Message factory ──────────────────────────────────────────────────────

    /**
     * Creates a new CHAT message from {@code senderId} to {@code receiverId}
     * containing {@code text}.  A fresh UUID and the current system timestamp
     * are assigned automatically.
     *
     * @param senderId   the local user's identifier
     * @param receiverId the remote peer's identifier
     * @param text       the chat text (may contain spaces, pipes, Unicode, etc.)
     * @return a ready-to-encode {@link Message}
     */
    public Message createChatMessage(
            String senderId,
            String receiverId,
            String text
    ) {
        return Message.create(MessageType.CHAT, senderId, receiverId, text);
    }

    // ── Codec façade ─────────────────────────────────────────────────────────

    /**
     * Encodes a {@link Message} into a wire string.
     * This string can be passed directly to
     * {@code PeerConnectionManager.sendText()}.
     *
     * @param message the message to encode
     * @return encoded wire string
     */
    public String encodeForTransport(Message message) {
        return MessageCodec.encode(message);
    }

    /**
     * Decodes a wire string received from
     * {@code PeerConnectionManager.onText()} back into a {@link Message}.
     *
     * Returns {@code null} when the string is not a valid MeshLink
     * application message (e.g. a raw legacy string or a corrupted frame).
     * The caller must handle {@code null} gracefully — do not crash.
     *
     * @param wire the raw string from the networking layer
     * @return the decoded {@link Message}, or {@code null}
     */
    public Message decodeFromTransport(String wire) {
        return MessageCodec.decode(wire);
    }
}
