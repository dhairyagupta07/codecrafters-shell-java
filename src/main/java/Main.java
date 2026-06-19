import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine();

            if (input.equals("exit")) {
                break;
            }

            // echo
            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            }

            // type command
            else if (input.startsWith("type ")) {
                String cmd = input.substring(5);

                // builtins
                if (cmd.equals("echo") ||
                    cmd.equals("exit") ||
                    cmd.equals("type")) {
                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }

                // search PATH
                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(":");
                boolean found = false;

                for (String path : paths) {
                    File file = new File(path, cmd);
                    if (file.exists() && file.canExecute()) {
                        System.out.println(cmd + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.out.println(cmd + ": not found");
                }
            }

            // unknown command
            else {
                System.out.println(input + ": command not found");
            }
        }

        sc.close();
    }
}