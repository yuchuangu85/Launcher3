// Compile-only stub of the device PersonalContextManager (real impl references private
// framework permission constants absent from the SDK). Only the methods referenced by
// embedded client code are declared; unused framework wiring omitted.
package android.service.personalcontext;

import android.annotation.NonNull;
import android.content.Context;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.insight.ContextInsight;

import java.util.List;
import java.util.Set;

public class PersonalContextManager {
    private final Context mContext;

    public static PersonalContextManager getInstance(Context context) {
        return new PersonalContextManager(context);
    }

    private PersonalContextManager(Context context) {
        mContext = context;
    }

    public void publishInsightSurfaceHints(@NonNull Set<ContextHint> hints,
            @NonNull InsightSurfaceClientInfo clientInfo) {}

    public void registerInsightSurfaceClient(@NonNull InsightSurfaceClientInfo clientInfo) {}

    public void unregisterInsightSurfaceClient(@NonNull InsightSurfaceClientInfo clientInfo) {}

    public void updateEmbeddedClientInfo(@NonNull InsightSurfaceClientInfo oldClientInfo,
            @NonNull InsightSurfaceClientInfo newClientInfo) {}

    public boolean isEnabled() { return false; }

    public boolean isPersonalContextModeEnabled(@NonNull String packageName) { return false; }
}
