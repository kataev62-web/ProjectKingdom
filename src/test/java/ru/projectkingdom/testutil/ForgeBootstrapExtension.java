package ru.projectkingdom.testutil;

import java.util.ArrayList;

import net.minecraftforge.network.NetworkEvent;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Prepares Forge's event listener metadata for tests running without ModLauncher. */
public final class ForgeBootstrapExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        // Forge normally transforms events to supply no-argument constructors.
        // Plain JUnit does not run that transformer. Asking actual event instances
        // for their listener lists uses Event's supported parent-linked fallback
        // and caches the same metadata NetworkHooks needs during Bootstrap.
        new NetworkEvent(() -> null).getListenerList();
        new NetworkEvent.GatherLoginPayloadsEvent(new ArrayList<>(), false).getListenerList();
    }
}
