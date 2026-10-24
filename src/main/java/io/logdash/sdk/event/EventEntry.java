package io.logdash.sdk.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable data container representing an event to be sent to Logdash.
 *
 * <p>An event entry consists of:
 *
 * <ul>
 *   <li>A name matching {@code ^[a-z][a-z0-9_]{0,63}$}, such as {@code agent_run}
 *   <li>Flat properties with string, number or boolean values
 *   <li>The signed-in user's internal ID, never an email address
 *   <li>A UUID v4 that retries reuse, so the event is stored once
 * </ul>
 *
 * @param name   the event name, must match {@code ^[a-z][a-z0-9_]{0,63}$}
 * @param props  the event properties; null keys and values are left out, and values other than
 *               strings, numbers and booleans become strings
 * @param userId the signed-in user's internal ID of up to 256 characters, never an email address;
 *               null when not set or blank
 * @param id     the UUID v4 of this event, generated when null
 */
public record EventEntry(String name, Map<String, Object> props, String userId, UUID id) {
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> WEBSITE_TRACKER_EVENTS =
            Set.of("pageview", "pageleave", "browser_error");
    private static final int MAX_USER_ID_LENGTH = 256;

    /**
     * Validates and normalizes the event entry parameters during construction.
     *
     * @throws IllegalArgumentException if the name is invalid or belongs to the website tracker, or
     *                                  the user ID contains {@code @} or is too long
     */
    public EventEntry {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Event name must match ^[a-z][a-z0-9_]{0,63}$, got: " + name);
        }
        if (WEBSITE_TRACKER_EVENTS.contains(name)) {
            throw new IllegalArgumentException(
                    "Event name " + name + " belongs to the website tracker");
        }
        if (userId != null && userId.isBlank()) {
            userId = null;
        }
        if (userId != null && userId.contains("@")) {
            throw new IllegalArgumentException(
                    "User ID of event " + name + " must be an internal ID, never an email address");
        }
        if (userId != null && userId.length() > MAX_USER_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "User ID of event " + name + " must be at most " + MAX_USER_ID_LENGTH + " characters");
        }
        props = flatten(props);
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    /**
     * Creates an event entry with a new UUID v4.
     *
     * @param name   the event name
     * @param props  the event properties
     * @param userId the signed-in user's internal ID, or null
     */
    public EventEntry(String name, Map<String, Object> props, String userId) {
        this(name, props, userId, null);
    }

    /**
     * Returns the properties of this event, in the order they were given.
     *
     * @return an immutable copy of the props map
     */
    public Map<String, Object> props() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(props));
    }

    private static Map<String, Object> flatten(Map<String, Object> props) {
        var flat = new LinkedHashMap<String, Object>();
        if (props != null) {
            props.forEach(
                    (key, value) -> {
                        if (key != null && value != null) {
                            flat.put(
                                    key,
                                    value instanceof Number || value instanceof Boolean
                                            ? value
                                            : String.valueOf(value));
                        }
                    });
        }
        return flat;
    }
}
