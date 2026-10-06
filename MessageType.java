/**
 * Application-level message types for MeshLink.
 *
 * Only CHAT is functionally used in the current prototype.
 * FILE and BULLETIN are declared so the codec and message layer
 * are already extensible without future protocol changes.
 */
public enum MessageType {
    CHAT,
    FILE,
    BULLETIN
}
