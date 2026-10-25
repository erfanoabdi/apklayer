// android.net.ParseException
//
// Thrown by MailTo / WebAddress when input cannot be parsed. AOSP makes it a
// RuntimeException with a public `response` field. Mirrors AOSP
// android.net.ParseException.
package android.net;

public class ParseException extends RuntimeException {
    public String response;

    ParseException(String response) {
        super(response);
        this.response = response;
    }
}
