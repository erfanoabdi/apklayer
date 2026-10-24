// android.view.textclassifier.ConversationActions -- the suggested actions for a
// conversation, plus the Request/Message shapes an app supplies.
// Source: frameworks/base
// core/java/android/view/textclassifier/ConversationActions.java (android16-release).
//
// Mac.apk has no on-device model, so suggestConversationActions returns an
// instance carrying an EMPTY list -- AOSP's own no-model answer.
package android.view.textclassifier;

public final class ConversationActions implements android.os.Parcelable {
    private final java.util.List<ConversationAction> mConversationActions;
    private final String mId;

    public ConversationActions(java.util.List<ConversationAction> conversationActions, String id) {
        mConversationActions = java.util.Collections.unmodifiableList(
                java.util.Objects.requireNonNull(conversationActions));
        mId = id;
    }

    public java.util.List<ConversationAction> getConversationActions() {
        return mConversationActions;
    }
    public String getId() { return mId; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeTypedList(mConversationActions);
        dest.writeString(mId);
    }
    public static final android.os.Parcelable.Creator<ConversationActions> CREATOR =
            new android.os.Parcelable.Creator<ConversationActions>() {
        @Override public ConversationActions createFromParcel(android.os.Parcel in) {
            java.util.List<ConversationAction> list =
                    in.createTypedArrayList(ConversationAction.CREATOR);
            return new ConversationActions(
                    list != null ? list
                                 : java.util.Collections.<ConversationAction>emptyList(),
                    in.readString());
        }
        @Override public ConversationActions[] newArray(int size) {
            return new ConversationActions[size];
        }
    };

    /** AOSP ConversationActions.Message: one turn of the conversation. */
    public static final class Message implements android.os.Parcelable {
        /** AOSP: the Person marking a message written by the device user. */
        public static final android.app.Person PERSON_USER_SELF =
                new android.app.Person.Builder().setKey("text-classifier-conversation-actions-user-self").build();
        /** AOSP: the Person marking a message from anyone else. */
        public static final android.app.Person PERSON_USER_OTHERS =
                new android.app.Person.Builder().setKey("text-classifier-conversation-actions-user-others").build();

        private final android.app.Person mAuthor;
        private final java.time.ZonedDateTime mReferenceTime;
        private final CharSequence mText;
        private final android.os.Bundle mExtras;

        private Message(Builder b) {
            mAuthor = b.mAuthor;
            mReferenceTime = b.mReferenceTime;
            mText = b.mText;
            mExtras = b.mExtras;
        }

        public android.app.Person getAuthor() { return mAuthor; }
        public java.time.ZonedDateTime getReferenceTime() { return mReferenceTime; }
        public CharSequence getText() { return mText; }
        public android.os.Bundle getExtras() { return mExtras; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeParcelable(mAuthor, flags);
            dest.writeString(mReferenceTime == null ? null : mReferenceTime.toString());
            dest.writeCharSequence(mText);
            dest.writeBundle(mExtras);
        }
        public static final android.os.Parcelable.Creator<Message> CREATOR =
                new android.os.Parcelable.Creator<Message>() {
            @Override public Message createFromParcel(android.os.Parcel in) {
                android.app.Person author = in.readParcelable(
                        android.app.Person.class.getClassLoader());
                Builder b = new Builder(author);
                String time = in.readString();
                if (time != null) b.setReferenceTime(java.time.ZonedDateTime.parse(time));
                b.setText(in.readCharSequence());
                android.os.Bundle extras = in.readBundle();
                if (extras != null) b.setExtras(extras);
                return b.build();
            }
            @Override public Message[] newArray(int size) { return new Message[size]; }
        };

        public static final class Builder {
            private final android.app.Person mAuthor;
            private java.time.ZonedDateTime mReferenceTime;
            private CharSequence mText;
            private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

            public Builder(android.app.Person author) {
                mAuthor = java.util.Objects.requireNonNull(author);
            }
            public Builder setText(CharSequence text) { mText = text; return this; }
            public Builder setReferenceTime(java.time.ZonedDateTime referenceTime) {
                mReferenceTime = referenceTime; return this;
            }
            public Builder setExtras(android.os.Bundle bundle) {
                mExtras = bundle != null ? bundle : android.os.Bundle.EMPTY; return this;
            }
            public Message build() { return new Message(this); }
        }
    }

    /** AOSP ConversationActions.Request: the conversation plus what the caller
     *  wants back. */
    public static final class Request implements android.os.Parcelable {
        public static final String HINT_FOR_NOTIFICATION = "install_notification";
        public static final String HINT_FOR_IN_APP = "in_app";

        private final java.util.List<Message> mConversation;
        private final TextClassifier.EntityConfig mTypeConfig;
        private final int mMaxSuggestions;
        private final java.util.List<String> mHints;
        private final android.os.Bundle mExtras;
        private String mCallingPackageName;

        private Request(Builder b) {
            mConversation = b.mConversation;
            mTypeConfig = b.mTypeConfig;
            mMaxSuggestions = b.mMaxSuggestions;
            mHints = b.mHints;
            mExtras = b.mExtras;
        }

        public java.util.List<Message> getConversation() { return mConversation; }
        public TextClassifier.EntityConfig getTypeConfig() { return mTypeConfig; }
        public int getMaxSuggestions() { return mMaxSuggestions; }
        public java.util.List<String> getHints() { return mHints; }
        public android.os.Bundle getExtras() { return mExtras; }
        public String getCallingPackageName() { return mCallingPackageName; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeTypedList(mConversation);
            dest.writeParcelable(mTypeConfig, flags);
            dest.writeInt(mMaxSuggestions);
            dest.writeStringList(mHints);
            dest.writeBundle(mExtras);
            dest.writeString(mCallingPackageName);
        }
        public static final android.os.Parcelable.Creator<Request> CREATOR =
                new android.os.Parcelable.Creator<Request>() {
            @Override public Request createFromParcel(android.os.Parcel in) {
                java.util.List<Message> conversation = in.createTypedArrayList(Message.CREATOR);
                Builder b = new Builder(conversation != null
                        ? conversation : java.util.Collections.<Message>emptyList());
                b.setTypeConfig((TextClassifier.EntityConfig) in.readParcelable(
                        TextClassifier.EntityConfig.class.getClassLoader()));
                b.setMaxSuggestions(in.readInt());
                b.setHints(in.createStringArrayList());
                android.os.Bundle extras = in.readBundle();
                if (extras != null) b.setExtras(extras);
                Request r = b.build();
                r.mCallingPackageName = in.readString();
                return r;
            }
            @Override public Request[] newArray(int size) { return new Request[size]; }
        };

        public static final class Builder {
            private final java.util.List<Message> mConversation;
            private TextClassifier.EntityConfig mTypeConfig =
                    new TextClassifier.EntityConfig.Builder().build();
            private int mMaxSuggestions = -1;
            private java.util.List<String> mHints = java.util.Collections.emptyList();
            private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

            public Builder(java.util.List<Message> conversation) {
                mConversation = java.util.Objects.requireNonNull(conversation);
            }
            public Builder setTypeConfig(TextClassifier.EntityConfig typeConfig) {
                mTypeConfig = typeConfig != null
                        ? typeConfig : new TextClassifier.EntityConfig.Builder().build();
                return this;
            }
            public Builder setMaxSuggestions(int maxSuggestions) {
                if (maxSuggestions < -1) {
                    throw new IllegalArgumentException("maxSuggestions has to be greater than or "
                            + "equal to -1.");
                }
                mMaxSuggestions = maxSuggestions;
                return this;
            }
            public Builder setHints(java.util.List<String> hints) {
                mHints = hints != null ? hints : java.util.Collections.<String>emptyList();
                return this;
            }
            public Builder setExtras(android.os.Bundle bundle) {
                mExtras = bundle != null ? bundle : android.os.Bundle.EMPTY; return this;
            }
            public Request build() { return new Request(this); }
        }
    }
}
