package ken5005.kreminder;

import java.time.LocalDateTime;

public class Reminder {

    public enum Priority { Pri1, Pri2, Pri3, Pri4, Pri5 }

    public LocalDateTime fireAt;
    public String message;
    public Priority priority;
    public String action;   // TODO: execute on fire (v0.1 stores only)
    public boolean noticed;
    public String repeat;   // TODO: parse repeat spec (v0.1 stores only)

    public Reminder() {
        priority = Priority.Pri3;
        action = "";
        repeat = "";
    }
}
