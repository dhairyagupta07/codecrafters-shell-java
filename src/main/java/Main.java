import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        String currentDir = System.getProperty("user.dir");

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine();
            if (input.isEmpty()) continue;

            if (input.equals("exit")) break;

            ArrayList<String> tokens = new ArrayList<>();
            StringBuilder sb = new StringBuilder();

            boolean inSingle = false;
            boolean inDouble = false;

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                if (inSingle) {
                    if (c == '\'') {
                        inSingle = false;
                    } else {
                        sb.append(c);
                    }
                    continue;
                }

                if (inDouble) {
                    if (c == '\\') {
                        if (i + 1 < input.length()) {
                            char n = input.charAt(i + 1);
                            if (n == '"' || n == '\\' || n == '$' || n == '`') {
                                sb.append(n);
                                i++;
                            } else {
                                sb.append('\\');
                            }
                        } else {
                            sb.append('\\');
                        }
                        continue;
                    }

                    if (c == '"') {
                        inDouble = false;
                        continue;
                    }

                    sb.append(c);
                    continue;
                }

                if (c == '\'') {
                    inSingle = true;
                    continue;
                }

                if (c == '"') {
                    inDouble = true;
                    continue;
                }

                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        sb.append(input.charAt(i + 1));
                        i++;
                    }
                    continue;
                }

                if (c == ' ') {
                    if (sb.length() > 0) {
                        tokens.add(sb.toString());
                        sb.setLength(0);
                    }
                } else {
                    sb.append(c);
                }
            }

            if (sb.length() > 0) tokens.add(sb.toString());
            if (tokens.isEmpty()) continue;

            String outFile = null;
            ArrayList<String> cmdTokens = new ArrayList<>();

            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).equals(">")) {
                    outFile = tokens.get(i + 1);
                    break;
                }
                cmdTokens.add(tokens.get(i));
            }

            String command = cmdTokens.get(0);

            if (command.equals("echo")) {
                String output = cmdTokens.size() > 1
                        ? String.join(" ", cmdTokens.subList(1, cmdTokens.size()))
                        : "";

                if (outFile != null) {
                    FileOutputStream fos = new FileOutputStream(new File(currentDir, outFile));
                    fos.write(output.getBytes());
                    fos.close();
                } else {
                    System.out.println(output);
                }
            }

            else if (command.equals("pwd")) {
                String output = currentDir;

                if (outFile != null) {
                    FileOutputStream fos = new FileOutputStream(new File(currentDir, outFile));
                    fos.write(output.getBytes());
                    fos.close();
                } else {
                    System.out.println(output);
                }
            }

            else if (command.equals("cd")) {
                String path = cmdTokens.size() > 1 ? cmdTokens.get(1) : "";

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
                String cmdName = cmdTokens.get(1);

                if (cmdName.equals("echo") || cmdName.equals("exit") || cmdName.equals("type")
                        || cmdName.equals("pwd") || cmdName.equals("cd")) {
                    System.out.println(cmdName + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    String[] paths = pathEnv.split(":");
                    boolean found = false;

                    for (String p : paths) {
                        File f = new File(p, cmdName);
                        if (f.exists() && f.canExecute()) {
                            System.out.println(cmdName + " is " + f.getAbsolutePath());
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
                    ProcessBuilder pb = new ProcessBuilder(cmdTokens);
                    pb.directory(new File(currentDir));

                    if (outFile != null) {
                        pb.redirectOutput(new File(currentDir, outFile));
                    } else {
                        pb.inheritIO();
                    }

                    Process process = pb.start();
                    process.waitFor();
                } catch (Exception e) {
                    System.out.println(cmdTokens.get(0) + ": command not found");
                }
            }
        }

        sc.close();
    }
}