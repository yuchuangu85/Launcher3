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
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.assist.ActivityId;
import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.personalcontext.Flags;
import android.view.autofill.AutofillId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A snapshot of a conversation's data at a specific moment in time.
 *
 * <p>This data originates from the Android Content Capture API and represents the state of a
 * conversation as displayed on-screen in a messaging application. It includes not only the visible
 * {@link ChatMessageData chat messages}, but also contextual metadata about the conversation's UI,
 * such as the app's {@link ComponentName}, the conversation title, the text in the input box, and
 * whether the keyboard is visible. This comprehensive snapshot provides the necessary context for
 * on-device models.
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
public final class ConversationData implements Parcelable {

    private final @Nullable ActivityId mActivityId;
    private final @NonNull Instant mProcessingStartTimestamp;
    private final @NonNull Instant mProcessingEndTimestamp;
    private final @NonNull ComponentName mComponentName;
    private final @Nullable AutofillId mInputBoxAutofillId;
    private final @NonNull String mInputBoxText;
    private final @NonNull String mConversationTitle;
    private final boolean mIsKeyboardShown;
    private final boolean mIsLastMessageFromTheUser;

    private final boolean mHasNewMessage;
    private final @NonNull List<ChatMessageData> mChatMessages;

    private ConversationData(
            @Nullable ActivityId activityId,
            @NonNull Instant processingStartTimestamp,
            @NonNull Instant processingEndTimestamp,
            @NonNull ComponentName componentName,
            @Nullable AutofillId inputBoxAutofillId,
            @NonNull String inputBoxText,
            @NonNull String conversationTitle,
            boolean isKeyboardShown,
            boolean isLastMessageFromTheUser,
            boolean hasNewMessage,
            @NonNull List<ChatMessageData> chatMessages) {
        mActivityId = activityId;
        mProcessingStartTimestamp = processingStartTimestamp;
        mProcessingEndTimestamp = processingEndTimestamp;
        mComponentName = componentName;
        mInputBoxAutofillId = inputBoxAutofillId;
        mInputBoxText = inputBoxText;
        mConversationTitle = conversationTitle;
        mIsKeyboardShown = isKeyboardShown;
        mIsLastMessageFromTheUser = isLastMessageFromTheUser;
        mHasNewMessage = hasNewMessage;
        mChatMessages = chatMessages;
    }

    private ConversationData(Parcel in) {
        mActivityId = in.readTypedObject(ActivityId.CREATOR);
        mProcessingStartTimestamp = Instant.ofEpochMilli(in.readLong());
        mProcessingEndTimestamp = Instant.ofEpochMilli(in.readLong());
        mComponentName = in.readTypedObject(ComponentName.CREATOR);
        mInputBoxAutofillId = in.readTypedObject(AutofillId.CREATOR);
        mInputBoxText = in.readString8();
        mConversationTitle = in.readString8();
        mIsKeyboardShown = in.readBoolean();
        mIsLastMessageFromTheUser = in.readBoolean();
        mHasNewMessage = in.readBoolean();
        mChatMessages = in.createTypedArrayList(ChatMessageData.CREATOR);
    }

    /**
     * Returns the id of the activity that contains the conversation.
     *
     * @hide
     */
    @SystemApi
    @Nullable
    public ActivityId getActivityId() {
        return mActivityId;
    }

    /**
     * Returns the timestamp when Content Capture first saw the message and the processing of the
     * conversation started.
     */
    @NonNull
    public Instant getProcessingStartTimestamp() {
        return mProcessingStartTimestamp;
    }

    /** Returns the timestamp when Content Capture finished processing the conversation. */
    @NonNull
    public Instant getProcessingEndTimestamp() {
        return mProcessingEndTimestamp;
    }

    /** Returns the component name of the activity that contains the conversation. */
    @NonNull
    public ComponentName getComponentName() {
        return mComponentName;
    }

    /**
     * Returns the autofill id of the input box in the conversation.
     *
     * <p>This may be null if Content Capture failed to find the input box or if autofill is
     * disabled.
     */
    @Nullable
    public AutofillId getInputBoxAutofillId() {
        return mInputBoxAutofillId;
    }

    /** Returns the text in the input box. */
    @NonNull
    public String getInputBoxText() {
        return mInputBoxText;
    }

    /** Returns the title of the conversation. */
    @NonNull
    public String getConversationTitle() {
        return mConversationTitle;
    }

    /**
     * Returns whether the keyboard was shown at the time Content Capture parsed the conversation.
     *
     * <p>This can be used by the understander as a signal to decide what models to run.
     */
    public boolean isKeyboardShown() {
        return mIsKeyboardShown;
    }

    /** Returns whether the last message in the conversation is from the user. */
    public boolean isLastMessageFromTheUser() {
        return mIsLastMessageFromTheUser;
    }

    /** Returns whether there is a new message in view. */
    public boolean hasNewMessage() {
        return mHasNewMessage;
    }

    /**
     * Returns the chat messages in the conversation.
     *
     * <p>The list will be sorted by order of earliest appearance in the conversation, matching what
     * the user sees in the UI.
     */
    @NonNull
    public List<ChatMessageData> getChatMessages() {
        return mChatMessages;
    }

    /**
     * Writes conversation data to the given {@link Parcel} for signing purposes. This method should
     * only write marshallable data, ie. no binders or FDs. The exact order is not important.
     */
    void writeToSignatureParcel(@NonNull Parcel dest) {
        dest.writeLong(mProcessingStartTimestamp.toEpochMilli());
        dest.writeLong(mProcessingEndTimestamp.toEpochMilli());
        dest.writeTypedObject(mComponentName, /* flags= */ 0);
        dest.writeTypedObject(mInputBoxAutofillId, /* flags= */ 0);
        dest.writeString8(mInputBoxText);
        dest.writeString8(mConversationTitle);
        dest.writeBoolean(mIsKeyboardShown);
        dest.writeBoolean(mIsLastMessageFromTheUser);
        dest.writeTypedList(mChatMessages);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mActivityId, flags);
        dest.writeLong(mProcessingStartTimestamp.toEpochMilli());
        dest.writeLong(mProcessingEndTimestamp.toEpochMilli());
        dest.writeTypedObject(mComponentName, flags);
        dest.writeTypedObject(mInputBoxAutofillId, flags);
        dest.writeString8(mInputBoxText);
        dest.writeString8(mConversationTitle);
        dest.writeBoolean(mIsKeyboardShown);
        dest.writeBoolean(mIsLastMessageFromTheUser);
        dest.writeBoolean(mHasNewMessage);
        dest.writeTypedList(mChatMessages);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationData)) return false;
        ConversationData that = (ConversationData) o;
        return mIsKeyboardShown == that.mIsKeyboardShown
                && mIsLastMessageFromTheUser == that.mIsLastMessageFromTheUser
                && Objects.equals(mActivityId, that.mActivityId)
                && Objects.equals(mProcessingStartTimestamp, that.mProcessingStartTimestamp)
                && Objects.equals(mProcessingEndTimestamp, that.mProcessingEndTimestamp)
                && Objects.equals(mComponentName, that.mComponentName)
                && Objects.equals(mInputBoxAutofillId, that.mInputBoxAutofillId)
                && Objects.equals(mInputBoxText, that.mInputBoxText)
                && Objects.equals(mConversationTitle, that.mConversationTitle)
                && mHasNewMessage == that.mHasNewMessage
                && Objects.equals(mChatMessages, that.mChatMessages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mActivityId,
                mProcessingStartTimestamp,
                mProcessingEndTimestamp,
                mComponentName,
                mInputBoxAutofillId,
                mInputBoxText,
                mConversationTitle,
                mIsKeyboardShown,
                mIsLastMessageFromTheUser,
                mHasNewMessage,
                mChatMessages);
    }

    @Override
    public String toString() {
        return "ConversationData{"
                + "mActivityId="
                + mActivityId
                + ", mProcessingStartTimestamp="
                + mProcessingStartTimestamp
                + ", mProcessingEndTimestamp="
                + mProcessingEndTimestamp
                + ", mComponentName="
                + mComponentName
                + ", mInputBoxAutofillId="
                + mInputBoxAutofillId
                + ", mInputBoxText='"
                + mInputBoxText
                + "'"
                + ", mConversationTitle='"
                + mConversationTitle
                + "'"
                + ", mIsKeyboardShown="
                + mIsKeyboardShown
                + ", mIsLastMessageFromTheUser="
                + mIsLastMessageFromTheUser
                + ", mHasNewMessage="
                + mHasNewMessage
                + ", mChatMessages="
                + mChatMessages
                + "}";
    }

    /** Builder for {@link ConversationData}. */
    @FlaggedApi(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
    public static final class Builder {
        private @Nullable ActivityId mActivityId;
        private @Nullable Instant mProcessingStartTimestamp;
        private @Nullable Instant mProcessingEndTimestamp;
        private @Nullable ComponentName mComponentName;
        private @Nullable AutofillId mInputBoxAutofillId;
        private @Nullable String mInputBoxText;
        private @Nullable String mConversationTitle;
        private @Nullable Boolean mIsKeyboardShown;
        private @Nullable Boolean mIsLastMessageFromTheUser;

        private @NonNull Boolean mHasNewMessage;
        private @Nullable List<ChatMessageData> mChatMessages;

        public Builder() {}

        /**
         * Sets whether the keyboard was shown at the time Content Capture parsed the conversation.
         *
         * <p>This can be used by the understander as a signal to decide what models to run. Since
         * this state may have changed since the hint was created, renderers should still check if
         * the keyboard is showing before showing results.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setKeyboardShown(boolean isKeyboardShown) {
            mIsKeyboardShown = isKeyboardShown;
            return this;
        }

        /**
         * Sets whether the last message in the conversation is from the user.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setLastMessageFromTheUser(boolean isLastMessageFromTheUser) {
            mIsLastMessageFromTheUser = isLastMessageFromTheUser;
            return this;
        }

        /**
         * Sets whether there is a new message in view.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setHasNewMessage(boolean hasNewMessage) {
            mHasNewMessage = hasNewMessage;
            return this;
        }

        /**
         * Sets the id of the activity that contains the conversation.
         *
         * <p>This setter is not required to build the {@link ConversationData}.
         *
         * @hide
         */
        @SystemApi
        @NonNull
        public Builder setActivityId(@Nullable ActivityId activityId) {
            mActivityId = activityId;
            return this;
        }

        /**
         * Sets the timestamp when Content Capture first saw the message and the processing of the
         * conversation started.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setProcessingStartTimestamp(@NonNull Instant processingStartTimestamp) {
            mProcessingStartTimestamp = processingStartTimestamp;
            return this;
        }

        /**
         * Sets the timestamp when Content Capture finished processing the conversation.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setProcessingEndTimestamp(@NonNull Instant processingEndTimestamp) {
            mProcessingEndTimestamp = processingEndTimestamp;
            return this;
        }

        /**
         * Sets the component name of the activity that contains the conversation.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setComponentName(@NonNull ComponentName componentName) {
            mComponentName = componentName;
            return this;
        }

        /**
         * Sets the autofill id of the input box in the conversation.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setInputBoxAutofillId(@Nullable AutofillId inputBoxAutofillId) {
            mInputBoxAutofillId = inputBoxAutofillId;
            return this;
        }

        /**
         * Sets the text in the input box.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setInputBoxText(@NonNull String inputBoxText) {
            mInputBoxText = inputBoxText;
            return this;
        }

        /**
         * Sets the title of the conversation.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setConversationTitle(@NonNull String conversationTitle) {
            mConversationTitle = conversationTitle;
            return this;
        }

        /**
         * Sets the chat messages in the conversation.
         *
         * <p>The list should be sorted by order of earliest appearance in the conversation,
         * matching what the user sees in the UI.
         *
         * <p>This is a required builder input.
         */
        @NonNull
        public Builder setChatMessages(@NonNull List<ChatMessageData> chatMessages) {
            mChatMessages = chatMessages;
            return this;
        }

        /**
         * Builds the {@link ConversationData}. All setters are required other than {@link
         * #setActivityId(ActivityId)}.
         */
        @NonNull
        public ConversationData build() {
            return new ConversationData(
                    mActivityId,
                    requireNonNull(
                            mProcessingStartTimestamp,
                            "ConversationData Builder: must use setProcessingStartTimestamp()"),
                    requireNonNull(
                            mProcessingEndTimestamp,
                            "ConversationData Builder: must use setProcessingEndTimestamp()"),
                    requireNonNull(
                            mComponentName,
                            "ConversationData Builder: must use setComponentName()"),
                    mInputBoxAutofillId,
                    requireNonNull(
                            mInputBoxText, "ConversationData Builder: must use setInputBoxText()"),
                    requireNonNull(
                            mConversationTitle,
                            "ConversationData Builder: must use setConversationTitle()"),
                    requireNonNull(
                            mIsKeyboardShown,
                            "ConversationData Builder: must use setKeyboardShown()"),
                    requireNonNull(
                            mIsLastMessageFromTheUser,
                            "ConversationData Builder: must use setLastMessageFromTheUser()"),
                    requireNonNull(
                            mHasNewMessage,
                            "ConversationData Builder: must use setHasNewMessage()"),
                    requireNonNull(
                            mChatMessages, "ConversationData Builder: must use setChatMessages()"));
        }
    }

    public static final @NonNull Creator<ConversationData> CREATOR =
            new Creator<>() {
                @Override
                public ConversationData createFromParcel(Parcel in) {
                    return new ConversationData(in);
                }

                @Override
                public ConversationData[] newArray(int size) {
                    return new ConversationData[size];
                }
            };
}
