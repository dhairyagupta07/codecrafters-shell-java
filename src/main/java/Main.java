import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        String currentDir = System.getProperty("user.dir");

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;

            if (input.equals("exit")) {
                break;
            }

            String command;
            ArrayList<String> tokens = new ArrayList<>();

            char[] arr = input.toCharArray();
            StringBuilder sb = new StringBuilder();
            boolean inSingle = false;

            for (int i = 0; i < arr.length; i++) {
                char c = arr[i];

                if (c == '\'') {
                    inSingle = !inSingle;
                    continue;
                }

                if (!inSingle && c == ' ') {
                    if (sb.length() > 0) {
                        tokens.add(sb.toString());
                        sb.setLength(0);
                    }
                } else {
                    sb.append(c);
                }
            }

            if (sb.length() > 0) {
                tokens.add(sb.toString());
            }

            if (tokens.size() == 0) continue;

            command = tokens.get(0);

            if (command.equals("echo")) {
                if (tokens.size() > 1) {
                    System.out.println(String.join(" ", tokens.subList(1, tokens.size())));
                } else {
                    System.out.println();
                }
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDir);
            }

            else if (command.equals("cd")) {
                String path = tokens.size() > 1 ? tokens.get(1) : "";

                if (path.equals("~")) {
                    path = System.getenv("HOME");
                }

                File newDir = new File(path);

                if (!newDir.isAbsolute()) {
                    newDir = new File(currentDir, path);
                }

                try {
                    String canonicalPath = newDir.getCanonicalPath();
                    File finalDir = new File(canonicalPath);

                    if (finalDir.exists() && finalDir.isDirectory()) {
                        currentDir = canonicalPath;
                    } else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                } catch (Exception e) {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            }

            else if (command.equals("type")) {
                String cmdName = tokens.get(1);

                if (cmdName.equals("echo") || cmdName.equals("exit") || cmdName.equals("type") || cmdName.equals("pwd") || cmdName.equals("cd")) {
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

            else {
                try {
                    ProcessBuilder pb = new ProcessBuilder(tokens);
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