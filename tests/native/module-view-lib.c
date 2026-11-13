/* Real ELF with an unwindable frame, loaded under JDK and unrelated filenames. */
int module_view_call(void (*callback)(void *), void *data)
{
    callback(data);
    return 17;
}
