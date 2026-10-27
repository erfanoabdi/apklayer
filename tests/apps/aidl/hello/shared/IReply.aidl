package hello.shared;
oneway interface IReply {
    void onReply(String value, int originalCallerPid);
}
