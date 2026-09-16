package DroneSwarmSim.messaging;

/**
 * Represents an acknowledgment message.
 * This class encapsulates the response to a specific message type along with
 * the success status and an optional message providing additional context.
 */
public record Ack(MessageType inResponseTo, boolean success, String message) {
    /**
     * Constructs an acknowledgment message.
     *
     * @param inResponseTo the message type this acknowledgement is responding to
     * @param success      whether the operation related to the message was successful
     * @param message      an optional message providing additional context about the acknowledgment
     */
    public Ack { message = message == null ? "" : message; }

    /**
     * Returns a string representation of the acknowledgment message.
     * The string includes the message type this acknowledgment responds to,
     * the success status, and an optional message providing context.
     *
     * @return a string representing the acknowledgment message
     */
    @Override
    public String toString() {
        return "Ack{inResponseTo=" + inResponseTo + ", success=" + success
                + ", message='" + message + "'}";
    }
}
