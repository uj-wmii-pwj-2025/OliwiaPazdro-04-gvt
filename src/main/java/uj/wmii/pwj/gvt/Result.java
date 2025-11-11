package uj.wmii.pwj.gvt;
public record Result(Integer errorCode, String message) {
    public Result(String message) {
        this(null, message);
    }
}
