package hello.shared;
import android.os.Bundle;
import hello.shared.IReply;
interface IEcho {
    String echo(String text);
    Bundle inspect(in Bundle value);
    int pid();
    int callerPid();
    void callback(IReply reply);
}
