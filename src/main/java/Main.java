import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] parts = input.split(" ");
            String command = parts[0];

            // ---------------- EXIT ----------------
            if (command.equals("exit")) {
                break;
            }

            // ---------------- ECHO ----------------
            else if (command.equals("echo")) {
                if (parts.length > 1) {
                    System.out.println(input.substring(5));
                } else {
                    System.out.println();
                }
            }

            // ---------------- PWD ----------------
            else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            }

            // ---------------- TYPE ----------------
            else if (command.equals("type")) {
                String cmdName = parts[1];

                // Builtins
                if (cmdName.equals("echo") ||
                    cmdName.equals("exit") ||
                    cmdName.equals("type") ||
                    cmdName.equals("pwd")) {

                    System.out.println(cmdName + " is a shell builtin");
                    continue;
                }

                // PATH lookup
                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(":");

                boolean found = false;

                for (String path : paths) {
                    File file = new File(path, cmdName);

                    if (file.exists() && file.canExecute()) {
                        System.out.println(cmdName + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.out.println(cmdName + ": not found");
                }
            }

            // ---------------- RUN PROGRAM ----------------
            else {
                try {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.inheritIO();

                    Process process = pb.start();
                    process.waitFor();
                } catch (Exception e) {
                    System.out.println(input + ": command not found");
                }
            }
        }

        sc.close();
    }
}