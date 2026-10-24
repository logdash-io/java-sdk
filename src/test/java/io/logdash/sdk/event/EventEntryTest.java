package io.logdash.sdk.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EventEntryTest {

    private enum Plan {
        PRO
    }

    @Test
    void should_create_event_entry_with_new_uuid_v4() {
        // Act
        var eventEntry = new EventEntry("agent_run", Map.of("steps", 4), "42");

        // Assert
        assertThat(eventEntry.name()).isEqualTo("agent_run");
        assertThat(eventEntry.props()).containsExactly(entry("steps", 4));
        assertThat(eventEntry.userId()).isEqualTo("42");
        assertThat(eventEntry.id().version()).isEqualTo(4);
        assertThat(eventEntry.id()).isNotEqualTo(new EventEntry("agent_run", null, null).id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "agent_run", "step2_done"})
    void should_accept_valid_names(String name) {
        assertThat(new EventEntry(name, null, null).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Agent_Run", "agent-run", "agent run", "1st_run", "_run", "agent_run\n"})
    void should_throw_exception_for_invalid_name(String name) {
        assertThatThrownBy(() -> new EventEntry(name, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event name must match ^[a-z][a-z0-9_]{0,63}$");
    }

    @Test
    void should_limit_name_to_64_characters() {
        assertThatNoException().isThrownBy(() -> new EventEntry("a".repeat(64), null, null));
        assertThatThrownBy(() -> new EventEntry("a".repeat(65), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pageview", "pageleave", "browser_error"})
    void should_throw_exception_for_website_tracker_name(String name) {
        assertThatThrownBy(() -> new EventEntry(name, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to the website tracker");
    }

    @Test
    void should_throw_exception_for_email_as_user_id_without_repeating_it() {
        assertThatThrownBy(() -> new EventEntry("signup", null, "ada@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never an email address")
                .hasMessageNotContaining("ada@example.com");
    }

    @Test
    void should_limit_user_id_to_256_characters() {
        assertThatNoException().isThrownBy(() -> new EventEntry("signup", null, "1".repeat(256)));
        assertThatThrownBy(() -> new EventEntry("signup", null, "1".repeat(257)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 256 characters");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void should_treat_blank_user_id_as_unset(String userId) {
        assertThat(new EventEntry("signup", null, userId).userId()).isNull();
    }

    @Test
    void should_keep_props_flat_and_in_order() {
        // Arrange
        var props = new LinkedHashMap<String, Object>();
        props.put("plan", Plan.PRO);
        props.put("steps", 4);
        props.put("skipped", null);
        props.put(null, "no key");
        props.put("success", true);
        props.put("model", "opus");

        // Act
        var eventEntry = new EventEntry("agent_run", props, null);

        // Assert
        assertThat(eventEntry.props())
                .containsExactly(
                        entry("plan", "PRO"), entry("steps", 4), entry("success", true), entry("model", "opus"));
    }

    @Test
    void should_copy_props_into_an_unmodifiable_map() {
        // Arrange
        var props = new HashMap<String, Object>(Map.of("steps", 4));
        var eventEntry = new EventEntry("agent_run", props, null);

        // Act
        props.put("model", "opus");

        // Assert
        assertThat(eventEntry.props()).containsOnlyKeys("steps");
        assertThatThrownBy(() -> eventEntry.props().put("model", "opus"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new EventEntry("agent_run", null, null).props()).isEmpty();
    }
}
