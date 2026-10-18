package com.saasresearcher.learning.support;

import com.saasresearcher.learning.learning.api.ProgressObserver;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestComponent;

/** Records progress notifications so tests can assert the integration seam fires. */
@TestComponent
public class RecordingProgressObserver implements ProgressObserver {

    private final List<UUID> changed = new CopyOnWriteArrayList<>();

    @Override
    public void progressChanged(UUID enrollmentId) {
        changed.add(enrollmentId);
    }

    public List<UUID> changed() {
        return List.copyOf(changed);
    }

    public void reset() {
        changed.clear();
    }
}
