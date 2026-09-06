/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext.hint;

import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationUpdateEvent;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * A hint that contains a conversation-related event from the Android Content Capture API.
 *
 * <p>The data encapsulated in this hint originates from the Android Content Capture API and
 * represents the state of a conversation from on-screen content, primarily from messaging
 * applications. It captures messages and metadata at a specific moment.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ContentCaptureConversationHint extends ContextHint {
    private static final String KEY_CONVERSATION_EVENT = "key_conversation_event";
    private final ContentCaptureConversationEvent mConversationEvent;

    private ContentCaptureConversationHint(
            @NonNull ConstructorParams baseParams,
            @NonNull ContentCaptureConversationEvent conversationEvent) {
        super(baseParams);
        mConversationEvent = requireNonNull(conversationEvent);
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    ContentCaptureConversationHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        this(
                baseParams,
                requireNonNull(
                        ContentCaptureConversationEvent.fromBundle(
                                requireNonNull(bundle.getBundle(KEY_CONVERSATION_EVENT))),
                        "Bundle must contain conversation event"));
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_CONVERSATION;
    }

    /** Returns the {@link ContentCaptureConversationEvent} contained in this hint. */
    @NonNull
    public ContentCaptureConversationEvent getConversationEvent() {
        return mConversationEvent;
    }

    /** @hide */
    @Nullable
    @Override
    public String getSourcePackageName() {
        if (mConversationEvent instanceof ConversationUpdateEvent updateEvent) {
            return updateEvent.getConversationData().getComponentName().getPackageName();
        }
        return null;
    }

    /** @hide */
    @Override
    public void writeToSignatureParcel(@NonNull Parcel dest) {
        mConversationEvent.writeToSignatureParcel(dest);
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        final Bundle bundle = new Bundle();
        bundle.putBundle(KEY_CONVERSATION_EVENT, mConversationEvent.toBundle());
        return bundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContentCaptureConversationHint)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ContentCaptureConversationHint that = (ContentCaptureConversationHint) o;
        return Objects.equals(mConversationEvent, that.mConversationEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mConversationEvent);
    }

    @Override
    public String toString() {
        return "ContentCaptureConversationHint{"
                + "mId="
                + getHintId()
                + ", mConversationEvent="
                + mConversationEvent
                + "}";
    }

    /** Builder for {@link ContentCaptureConversationHint}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private final ContentCaptureConversationEvent mConversationEvent;

        /**
         * Creates an instance of {@link Builder} with the {@link ContentCaptureConversationEvent}
         * contained in the hint.
         */
        public Builder(@NonNull ContentCaptureConversationEvent conversationEvent) {
            mConversationEvent = requireNonNull(conversationEvent);
        }

        /**
         * Adds a token to the resulting {@link ContentCaptureConversationHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /** Creates the {@link ContentCaptureConversationHint}. */
        @NonNull
        public ContentCaptureConversationHint build() {
            return new ContentCaptureConversationHint(mBaseBuilder.build(), mConversationEvent);
        }
    }
}
