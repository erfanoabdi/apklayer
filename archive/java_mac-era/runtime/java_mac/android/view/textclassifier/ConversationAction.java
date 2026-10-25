// android.view.textclassifier.ConversationAction -- one smart reply / action
// the classifier suggests for a conversation.
// Source: frameworks/base
// core/java/android/view/textclassifier/ConversationAction.java (android16-release).
//
// Mac.apk has no on-device model, so TextClassifier.suggestConversationActions
// returns an EMPTY list -- AOSP's own answer for a classifier with no model.
package android.view.textclassifier;

public final class ConversationAction implements android.os.Parcelable {
    public static final String TYPE_VIEW_CALENDAR = "view_calendar";
    public static final String TYPE_VIEW_MAP = "view_map";
    public static final String TYPE_TRACK_FLIGHT = "track_flight";
    public static final String TYPE_OPEN_URL = "open_url";
    public static final String TYPE_SEND_SMS = "send_sms";
    public static final String TYPE_CALL_PHONE = "call_phone";
    public static final String TYPE_SEND_EMAIL = "send_email";
    public static final String TYPE_TEXT_REPLY = "text_reply";
    public static final String TYPE_CREATE_REMINDER = "create_reminder";
    public static final String TYPE_SHARE_LOCATION = "share_location";

    private final String mType;
    private final CharSequence mTextReply;
    private final android.app.RemoteAction mAction;
    private final float mScore;
    private final android.os.Bundle mExtras;

    private ConversationAction(Builder b) {
        mType = b.mType;
        mTextReply = b.mTextReply;
        mAction = b.mAction;
        mScore = b.mScore;
        mExtras = b.mExtras;
    }

    public String getType() { return mType; }
    public android.app.RemoteAction getAction() { return mAction; }
    public CharSequence getTextReply() { return mTextReply; }
    public float getConfidenceScore() { return mScore; }
    public android.os.Bundle getExtras() { return mExtras; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeCharSequence(mTextReply);
        dest.writeParcelable(mAction, flags);
        dest.writeFloat(mScore);
        dest.writeBundle(mExtras);
    }
    public static final android.os.Parcelable.Creator<ConversationAction> CREATOR =
            new android.os.Parcelable.Creator<ConversationAction>() {
        @Override public ConversationAction createFromParcel(android.os.Parcel in) {
            Builder b = new Builder(in.readString());
            CharSequence reply = in.readCharSequence();
            if (reply != null) b.setTextReply(reply);
            b.setAction((android.app.RemoteAction) in.readParcelable(
                    android.app.RemoteAction.class.getClassLoader()));
            b.setConfidenceScore(in.readFloat());
            android.os.Bundle extras = in.readBundle();
            if (extras != null) b.setExtras(extras);
            return b.build();
        }
        @Override public ConversationAction[] newArray(int size) {
            return new ConversationAction[size];
        }
    };

    public static final class Builder {
        private final String mType;
        private CharSequence mTextReply;
        private android.app.RemoteAction mAction;
        private float mScore;
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

        public Builder(String actionType) {
            mType = java.util.Objects.requireNonNull(actionType);
        }
        public Builder setAction(android.app.RemoteAction action) { mAction = action; return this; }
        public Builder setTextReply(CharSequence textReply) { mTextReply = textReply; return this; }
        public Builder setConfidenceScore(float score) {
            if (score < 0 || score > 1) {
                throw new IllegalArgumentException("confidenceScore must be in [0, 1]");
            }
            mScore = score;
            return this;
        }
        public Builder setExtras(android.os.Bundle extras) {
            mExtras = extras != null ? extras : android.os.Bundle.EMPTY; return this;
        }
        public ConversationAction build() { return new ConversationAction(this); }
    }
}
