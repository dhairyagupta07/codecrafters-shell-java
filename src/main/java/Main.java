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
                    if (c == '\'') inSingle = false;
                    else sb.append(c);
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

            ArrayList<String> cmdTokens = new ArrayList<>();
            String outFile = null;

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);

                if (t.equals(">") || t.equals("1>")) {
                    if (i + 1 < tokens.size()) {
                        outFile = tokens.get(i + 1);
                    }
                    break;
                }

                cmdTokens.add(t);
            }

            if (cmdTokens.isEmpty()) continue;

            String command = cmdTokens.get(0);
            String output = null;

            if (command.equals("echo")) {
                output = cmdTokens.size() > 1
                        ? String.join(" ", cmdTokens.subList(1, cmdTokens.size()))
                        : "";
            }

            else if (command.equals("pwd")) {
                output = currentDir;
            }

            else if (command.equals("type")) {
                String name = cmdTokens.get(1);

                if (name.equals("echo") || name.equals("pwd") || name.equals("cd") ||
                    name.equals("type") || name.equals("exit")) {
                    output = name + " is a shell builtin";
                } else {
                    String[] paths = System.getenv("PATH").split(":");
                    boolean found = false;

                    for (String p : paths) {
                        File f = new File(p, name);
                        if (f.exists() && f.canExecute()) {
                            output = name + " is " + f.getAbsolutePath();
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        output = name + ": not found";
                    }
                }
            }

            else if (command.equals("cd")) {
                String path = cmdTokens.size() > 1 ? cmdTokens.get(1) : "";
                if (path.equals("~")) path = System.getenv("HOME");

                File f = new File(path);
                if (!f.isAbsolute()) {
                    f = new File(currentDir, path);
                }

                try {
                    String canon = f.getCanonicalPath();
                    File real = new File(canon);

                    if (real.exists() && real.isDirectory()) {
                        currentDir = canon;
                    } else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                } catch (Exception e) {
                    System.out.println("cd: " + path + ": No such file or directory");
                }

                continue;
            }

            else {
                try {
                    ProcessBuilder pb = new ProcessBuilder(cmdTokens);
                    pb.directory(new File(currentDir));

                    if (outFile != null) {
                        pb.redirectOutput(new File(currentDir, outFile));
                        pb.inheritIO(); // keep stderr on terminal
                    } else {
                        pb.inheritIO();
                    }

                    Process p = pb.start();
                    p.waitFor();
                } catch (Exception e) {
                    System.out.println(command + ": command not found");
                }
                continue;
            }

            if (outFile != null) {
                FileOutputStream fos = new FileOutputStream(new File(currentDir, outFile));
                fos.write(output.getBytes());
                fos.close();
            } else {
                System.out.println(output);
            }
        }

        sc.close();
    }
}