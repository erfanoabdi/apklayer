package androidx.window.extensions.embedding;

import androidx.window.extensions.core.util.function.Consumer;
import androidx.window.extensions.core.util.function.Function;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
/** Drives activity embedding (multi-pane splits). Mac.apk shows one activity
 *  full-screen and never embeds, so no activity is embedded, no split is ever
 *  reported, and rule/attribute mutations are inert. */
public interface ActivityEmbeddingComponent {
    default void setEmbeddingRules(Set<androidx.window.extensions.embedding.EmbeddingRule> rules) { }
    default void setSplitInfoCallback(java.util.function.Consumer<List<SplitInfo>> consumer) {
        if (consumer != null) consumer.accept(Collections.emptyList());
    }
    default void setSplitInfoCallback(Consumer<List<SplitInfo>> consumer) {
        if (consumer != null) consumer.accept(Collections.emptyList());
    }
    default void clearSplitInfoCallback() { }
    default boolean isActivityEmbedded(android.app.Activity activity) { return false; }
    default void setSplitAttributesCalculator(Function<SplitAttributesCalculatorParams, SplitAttributes> calculator) { }
    default void clearSplitAttributesCalculator() { }
    default void registerActivityStackCallback(Executor executor, Consumer<List<ActivityStack>> consumer) {
        if (consumer != null) { Runnable r = () -> consumer.accept(Collections.emptyList());
            if (executor != null) executor.execute(r); else r.run(); }
    }
    default void unregisterActivityStackCallback(Consumer<List<ActivityStack>> consumer) { }
    default void finishActivityStacksWithTokens(Set<ActivityStack.Token> tokens) { }
    default void invalidateTopVisibleSplitAttributes() { }
    default void updateSplitAttributes(android.os.IBinder splitInfoToken, SplitAttributes splitAttributes) { }
    default void updateSplitAttributes(SplitInfo.Token splitInfoToken, SplitAttributes splitAttributes) { }
    default boolean pinTopActivityStack(int taskId, SplitPinRule splitPinRule) { return false; }
    default void unpinTopActivityStack(int taskId) { }
    default void setAutoSaveEmbeddingState(boolean saveState) { }
    default EmbeddedActivityWindowInfo getEmbeddedActivityWindowInfo(android.app.Activity activity) {
        return new EmbeddedActivityWindowInfo(activity);
    }
    default void setEmbeddedActivityWindowInfoCallback(Executor executor, Consumer<EmbeddedActivityWindowInfo> callback) { }
    default void clearEmbeddedActivityWindowInfoCallback() { }
    default void setActivityStackAttributesCalculator(Function<ActivityStackAttributesCalculatorParams, ActivityStackAttributes> calculator) { }
    default void clearActivityStackAttributesCalculator() { }
    default void updateActivityStackAttributes(ActivityStack.Token token, ActivityStackAttributes attributes) { }
    default ActivityStack.Token getActivityStackToken(String tag) { return null; }
    default ParentContainerInfo getParentContainerInfo(ActivityStack.Token activityStackToken) { return null; }
}
