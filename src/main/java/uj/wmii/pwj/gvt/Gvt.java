package uj.wmii.pwj.gvt;

public class Gvt {

    private final ExitHandler exitHandler;
    private final Repository repository;

    public Gvt(ExitHandler exitHandler) {
        this.exitHandler = exitHandler;
        this.repository = new Repository();
    }

    public static void main(String... args) {
        Gvt gvt = new Gvt(new ExitHandler());
        gvt.mainInternal(args);
    }

    public void mainInternal(String... args) {

        if(args.length == 0) {
            exitHandler.exit(1, "Please specify command.");
            return;
        }
        String command = args[0];
        Result result = null;

        if (!"init".equals(command) && Repository.notInitialized()) {
            exitHandler.exit(-2, "Current directory is not initialized. Please use init command to initialize.");
            return;
        }

        switch (command) {
            case "init" -> result = repository.init();
            case "add" -> result = repository.add(args);
            case "detach" -> result = repository.detach(args);
            case "checkout" -> result = repository.checkout(args);
            case "commit" -> result = repository.commit(args);
            case "history" -> result = repository.history(args);
            case "version" -> result = repository.version(args);
            default -> exitHandler.exit(1, "Unknown command " + command + ".");
        }

        if(result != null) {
            if (result.errorCode() != null)
                exitHandler.exit(result.errorCode(), result.message());
            else
                exitHandler.exit(0, result.message());
        }
    }
}
