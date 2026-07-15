package com.aidatachat.adapters.out.provider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidatachat.application.exception.ProviderCommunicationException;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ProviderDestinationPolicyTest {

    @Test
    void allowsOnlyExplicitInternalHttpDestinations() {
        ProviderDestinationPolicy policy = new ProviderDestinationPolicy("127.0.0.1", "127.0.0.1");

        assertThatCode(() -> policy.validateCustomDestination(URI.create("http://127.0.0.1:11434")))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksHostsOutsideTheAllowlistAndPlainHttpWithoutExplicitPermission() {
        ProviderDestinationPolicy policy = new ProviderDestinationPolicy("127.0.0.1", "");

        assertBlocked(policy, "http://127.0.0.1:11434");
        assertBlocked(policy, "https://localhost:11434");
    }

    @Test
    void alwaysBlocksLinkLocalMetadataAddresses() {
        ProviderDestinationPolicy policy =
                new ProviderDestinationPolicy("169.254.169.254", "169.254.169.254");

        assertBlocked(policy, "http://169.254.169.254/latest/meta-data");
    }

    private void assertBlocked(ProviderDestinationPolicy policy, String uri) {
        assertThatThrownBy(() -> policy.validateCustomDestination(URI.create(uri)))
                .isInstanceOf(ProviderCommunicationException.class)
                .extracting(exception -> ((ProviderCommunicationException) exception).code())
                .isEqualTo("PROVIDER_DESTINATION_BLOCKED");
    }
}
