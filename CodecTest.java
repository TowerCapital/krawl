/**
 * Quick self-test for MessageCodec — verifies round-trip encode/decode
 * for chat text that contains spaces, pipes, punctuation, and Unicode.
 * Run with:  java CodecTest
 */
public class CodecTest {
    public static void main(String[] args) {
        MessageService svc = new MessageService();

        String[] payloads = {
            "Hello Bob!",
            "Hello Bob, this is MeshLink!",
            "Spaces and   tabs\there",
            "Pipe | characters | everywhere |",
            "Punctuation: !@#$%^&*()_+-=[]{}|;':\",./<>?",
            "Unicode: नमस्ते, 你好, مرحبا, Привет, こんにちは",
            "Mixed: Hello | World | नमस्ते | 你好 | Pipe|Test"
        };

        int passed = 0;
        int failed = 0;

        for (String payload : payloads) {
            Message original = svc.createChatMessage("alice", "bob", payload);
            String wire = svc.encodeForTransport(original);
            Message decoded = MessageCodec.decode(wire);

            if (decoded == null) {
                System.out.println("FAIL [decode returned null]: " + payload);
                failed++;
                continue;
            }

            boolean ok = decoded.getType()       == MessageType.CHAT
                      && decoded.getSenderId()  .equals("alice")
                      && decoded.getReceiverId().equals("bob")
                      && decoded.getPayload()   .equals(payload)
                      && decoded.getMessageId() .equals(original.getMessageId())
                      && decoded.getTimestamp() == original.getTimestamp();

            if (ok) {
                System.out.println("PASS: " + payload);
                passed++;
            } else {
                System.out.println("FAIL [mismatch]:");
                System.out.println("  expected payload : " + payload);
                System.out.println("  decoded  payload : " + decoded.getPayload());
                failed++;
            }
        }

        // Also verify that a raw / legacy string safely returns null.
        String legacy = "Hello raw world!";
        Message legacyDecoded = MessageCodec.decode(legacy);
        if (legacyDecoded == null) {
            System.out.println("PASS (legacy/null): raw string correctly returns null");
            passed++;
        } else {
            System.out.println("FAIL: raw string should return null but decoded to " + legacyDecoded);
            failed++;
        }

        System.out.println();
        System.out.println("Results: " + passed + " passed, " + failed + " failed.");
        System.exit(failed == 0 ? 0 : 1);
    }
}
