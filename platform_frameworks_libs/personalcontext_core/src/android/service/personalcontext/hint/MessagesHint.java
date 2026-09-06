/*
 * Copyright 2026 The Android Open Source Project
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
import android.service.personalcontext.Flags;
import android.service.personalcontext.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A hint that contains a list of chat messages from a single conversation.
 *
 * <p>Used by messaging applications to directly provide chat messages to generate personal context
 * suggestions for.
 *
 * <p>Messages should all originate from a single conversation. Mixing messages from multiple
 * conversations may cause insights to be generated that are not relevant to the conversation the
 * user is viewing.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class MessagesHint extends ContextHint {
    private static final String KEY_PACKAGE_NAME = "key_package_name";
    private static final String KEY_CHAT_MESSAGES = "key_chat_messages";

    private final String mPackageName;
    private final List<ChatMessageData> mChatMessages;

    /** Creates a new {@link MessagesHint}. */
    private MessagesHint(
            @NonNull ConstructorParams baseParams,
            @NonNull String packageName,
            @NonNull List<ChatMessageData> chatMessages) {
        super(baseParams);
        mPackageName = packageName;
        mChatMessages = List.copyOf(chatMessages);
    }

    /**
     * Internal constructor only for use by {@link ContextHint#createHintFromBundle(Bundle)}.
     *
     * @hide
     */
    MessagesHint(@NonNull ConstructorParams baseParams, @NonNull Bundle bundle) {
        super(baseParams);
        mPackageName = bundle.getString(KEY_PACKAGE_NAME);
        requireNonNull(mPackageName, "Bundle must contain package name");
        mChatMessages = bundle.getParcelableArrayList(KEY_CHAT_MESSAGES, ChatMessageData.class);
        requireNonNull(mChatMessages, "Bundle must contain chat messages list");
    }

    /** @hide */
    @Override
    @HintType
    public int getHintType() {
        return HINT_TYPE_MESSAGE;
    }

    /**
     * Returns the package name of the application the messages are from.
     *
     * <p>The understander may use this to tailor the content or format of suggestions based on the
     * application that is providing the messages.
     */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /** Returns a list of {@link ChatMessageData} objects. */
    @NonNull
    public List<ChatMessageData> getChatMessages() {
        return mChatMessages;
    }

    @NonNull
    @Override
    Bundle toBundleImpl() {
        final Bundle bundle = new Bundle();
        bundle.putString(KEY_PACKAGE_NAME, mPackageName);
        bundle.putParcelableArrayList(KEY_CHAT_MESSAGES, new ArrayList<>(mChatMessages));
        return bundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessagesHint)) return false;
        if (!super.equals(o)) return false;
        MessagesHint that = (MessagesHint) o;
        return Objects.equals(mPackageName, that.mPackageName)
                && Objects.equals(mChatMessages, that.mChatMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mPackageName, mChatMessages);
    }

    @Override
    public String toString() {
        return "MessagesHint{"
                + "mPackageName="
                + mPackageName
                + ", mChatMessages="
                + mChatMessages
                + "}";
    }

    /** Builder used to create a {@link MessagesHint}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private final ConstructorParams.Builder mBaseBuilder = new ConstructorParams.Builder();
        private String mPackageName;
        private List<ChatMessageData> mChatMessages = new ArrayList<>();

        /**
         * Constructs an instance of {@link Builder}.
         *
         * @param packageName the package name of the application the messages are from
         */
        public Builder(@NonNull String packageName) {
            mPackageName = requireNonNull(packageName);
        }

        /**
         * Adds a token to the resulting {@link MessagesHint}.
         *
         * @param token the token to add
         */
        @NonNull
        public Builder addToken(@NonNull Token token) {
            mBaseBuilder.addToken(token);
            return this;
        }

        /** Sets a list of {@link ChatMessageData} objects. */
        @NonNull
        public Builder setChatMessages(@NonNull List<ChatMessageData> chatMessages) {
            requireNonNull(chatMessages, "chatMessages must not be null");
            if (chatMessages.isEmpty()) {
                throw new IllegalArgumentException(
                        "chat messages must be provided with setChatMessages");
            }
            mChatMessages = List.copyOf(chatMessages);
            return this;
        }

        /** Returns the built {@link MessagesHint}. */
        @NonNull
        public MessagesHint build() {
            if (mChatMessages.isEmpty()) {
                throw new IllegalArgumentException(
                        "chat messages must be provided with setChatMessages");
            }
            return new MessagesHint(mBaseBuilder.build(), mPackageName, mChatMessages);
        }
    }
}
