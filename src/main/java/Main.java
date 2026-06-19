import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        // track current directory manually (IMPORTANT for cd)
        String currentDir = System.getProperty("user.dir");

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
                System.out.println(currentDir);
            }

            // ---------------- CD ----------------
            else if (command.equals("cd")) {
                String path = parts.length > 1 ? parts[1] : "";

                File newDir = new File(path);

                if (!newDir.isAbsolute()) {
                    newDir = new File(currentDir, path);
                }

                if (newDir.exists() && newDir.isDirectory()) {
                    currentDir = newDir.getCanonicalPath();
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            }

            // ---------------- TYPE ----------------
            else if (command.equals("type")) {
                String cmdName = parts[1];

                if (cmdName.equals("echo") ||
                    cmdName.equals("exit") ||
                    cmdName.equals("type") ||
                    cmdName.equals("pwd") ||
                    cmdName.equals("cd")) {

                    System.out.println(cmdName + " is a shell builtin");
                } else {
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
            }

            // ---------------- RUN PROGRAM ----------------
            else {
                try {
                    ProcessBuilder pb = new ProcessBuilder(parts);

                    // run in current directory (IMPORTANT for cd)
                    pb.directory(new File(currentDir));
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