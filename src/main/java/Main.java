import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

public class Main {

    static class BackgroundJob {
        int id;
        Process process;
        String commandStr;

        BackgroundJob(int id, Process process, String commandStr) {
            this.id = id;
            this.process = process;
            this.commandStr = commandStr;
        }
    }

    private static final List<BackgroundJob> activeJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        String currentDir = System.getProperty("user.dir");

        while (true) {
            reapCompletedJobs();

            System.out.print("$ ");
            System.out.flush();

            if (!sc.hasNextLine()) break;
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

            boolean isBackground = false;
            if (tokens.get(tokens.size() - 1).equals("&")) {
                isBackground = true;
                tokens.remove(tokens.size() - 1);
            }

            if (tokens.isEmpty()) continue;

            int pipeIndex = tokens.indexOf("|");
            if (pipeIndex != -1) {
                List<String> leftTokens = tokens.subList(0, pipeIndex);
                List<String> rightTokens = tokens.subList(pipeIndex + 1, tokens.size());
                handlePipeline(leftTokens, rightTokens, currentDir);
                continue;
            }

            handleSingleCommand(tokens, currentDir, isBackground);
        }

        sc.close();
    }

    private static void handleSingleCommand(List<String> tokens, String currentDir, boolean isBackground) throws Exception {
        ArrayList<String> cmdTokens = new ArrayList<>();
        String outFile = null;
        String errFile = null;
        boolean appendOut = false;
        boolean appendErr = false;

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);

            if (t.equals(">") || t.equals("1>")) {
                if (i + 1 < tokens.size()) {
                    outFile = tokens.get(i + 1);
                }
                break;
            } else if (t.equals(">>") || t.equals("1>>")) {
                if (i + 1 < tokens.size()) {
                    outFile = tokens.get(i + 1);
                    appendOut = true;
                }
                break;
            } else if (t.equals("2>")) {
                if (i + 1 < tokens.size()) {
                    errFile = tokens.get(i + 1);
                }
                break;
            } else if (t.equals("2>>")) {
                if (i + 1 < tokens.size()) {
                    errFile = tokens.get(i + 1);
                    appendErr = true;
                }
                break;
            }
            cmdTokens.add(t);
        }

        if (cmdTokens.isEmpty()) return;

        if (outFile != null) createOrPrepareFile(currentDir, outFile, appendOut);
        if (errFile != null) createOrPrepareFile(currentDir, errFile, appendErr);

        String command = cmdTokens.get(0);

        if (command.equals("jobs")) {
            executeJobsBuiltin(cmdTokens, outFile, currentDir);
        } else if (command.equals("echo")) {
            String output = String.join(" ", cmdTokens.subList(1, cmdTokens.size()));
            write(currentDir, outFile, output, false);
        } else if (command.equals("pwd")) {
            write(currentDir, outFile, currentDir, false);
        } else if (command.equals("cd")) {
            executeCdBuiltin(cmdTokens, errFile, currentDir);
        } else if (command.equals("type")) {
            executeTypeBuiltin(cmdTokens, outFile, currentDir);
        } else {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmdTokens);
                pb.directory(new File(currentDir));

                if (outFile != null) {
                    File targetFile = new File(outFile);
                    if (!targetFile.isAbsolute()) targetFile = new File(currentDir, outFile);
                    pb.redirectOutput(appendOut ? ProcessBuilder.Redirect.appendTo(targetFile) : ProcessBuilder.Redirect.to(targetFile));
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }

                if (errFile != null) {
                    File targetErrFile = new File(errFile);
                    if (!targetErrFile.isAbsolute()) targetErrFile = new File(currentDir, errFile);
                    pb.redirectError(appendErr ? ProcessBuilder.Redirect.appendTo(targetErrFile) : ProcessBuilder.Redirect.to(targetErrFile));
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process p = pb.start();
                if (isBackground) {
                    String rawCommandStr = String.join(" ", cmdTokens);
                    int jobId = getLowestAvailableJobId();
                    System.out.println("[" + jobId + "] " + p.pid());
                    System.out.flush();
                    activeJobs.add(new BackgroundJob(jobId, p, rawCommandStr));
                } else {
                    p.waitFor();
                }
            } catch (Exception e) {
                write(currentDir, errFile, command + ": command not found", true);
            }
        }
    }

    private static void handlePipeline(List<String> left, List<String> right, String currentDir) throws Exception {
        ArrayList<String> leftCmd = new ArrayList<>();
        for (String t : left) {
            if (t.equals(">") || t.equals("1>") || t.equals(">>") || t.equals("1>>") || t.equals("2>") || t.equals("2>>")) break;
            leftCmd.add(t);
        }

        ArrayList<String> rightCmd = new ArrayList<>();
        String outFile = null;
        String errFile = null;
        boolean appendOut = false;
        boolean appendErr = false;

        for (int i = 0; i < right.size(); i++) {
            String t = right.get(i);
            if (t.equals(">") || t.equals("1>")) {
                if (i + 1 < right.size()) outFile = right.get(i + 1);
                break;
            } else if (t.equals(">>") || t.equals("1>>")) {
                if (i + 1 < right.size()) { outFile = right.get(i + 1); appendOut = true; }
                break;
            } else if (t.equals("2>")) {
                if (i + 1 < right.size()) errFile = right.get(i + 1);
                break;
            } else if (t.equals("2>>")) {
                if (i + 1 < right.size()) { errFile = right.get(i + 1); appendErr = true; }
                break;
            }
            rightCmd.add(t);
        }

        if (outFile != null) createOrPrepareFile(currentDir, outFile, appendOut);
        if (errFile != null) createOrPrepareFile(currentDir, errFile, appendErr);

        ProcessBuilder pbLeft = new ProcessBuilder(leftCmd);
        pbLeft.directory(new File(currentDir));

        ProcessBuilder pbRight = new ProcessBuilder(rightCmd);
        pbRight.directory(new File(currentDir));

        pbLeft.redirectError(ProcessBuilder.Redirect.INHERIT);
        if (errFile != null) {
            File targetErrFile = new File(errFile);
            if (!targetErrFile.isAbsolute()) targetErrFile = new File(currentDir, errFile);
            pbRight.redirectError(appendErr ? ProcessBuilder.Redirect.appendTo(targetErrFile) : ProcessBuilder.Redirect.to(targetErrFile));
        } else {
            pbRight.redirectError(ProcessBuilder.Redirect.INHERIT);
        }

        if (outFile != null) {
            File targetFile = new File(outFile);
            if (!targetFile.isAbsolute()) targetFile = new File(currentDir, outFile);
            pbRight.redirectOutput(appendOut ? ProcessBuilder.Redirect.appendTo(targetFile) : ProcessBuilder.Redirect.to(targetFile));
        } else {
            pbRight.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }

        Process pLeft = pbLeft.start();
        Process pRight = pbRight.start();

        try (InputStream in = pLeft.getInputStream();
             OutputStream out = pRight.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                try {
                    out.write(buffer, 0, read);
                    out.flush();
                } catch (Exception e) {
                    break;
                }
            }
        }

        pLeft.destroy();
        pRight.waitFor();
    }

    private static void executeJobsBuiltin(List<String> cmdTokens, String outFile, String currentDir) throws Exception {
        int targetId = -1;
        if (cmdTokens.size() > 1 && cmdTokens.get(1).startsWith("%")) {
            try { targetId = Integer.parseInt(cmdTokens.get(1).substring(1)); } catch (NumberFormatException e) {}
        }

        StringBuilder jobsOutput = new StringBuilder();
        Iterator<BackgroundJob> it = activeJobs.iterator();
        int count = 0;
        int total = activeJobs.size();

        while (it.hasNext()) {
            BackgroundJob job = it.next();
            count++;
            if (targetId == -1 || job.id == targetId) {
                String sign = " ";
                if (count == total) sign = "+";
                else if (count == total - 1) sign = "-";
                
                if (!job.process.isAlive()) {
                    jobsOutput.append("[").append(job.id).append("]").append(sign).append("  Done                    ").append(job.commandStr).append("\n");
                    it.remove();
                } else {
                    jobsOutput.append("[").append(job.id).append("]").append(sign).append("  Running                 ").append(job.commandStr).append(" &\n");
                }
            } else if (!job.process.isAlive()) {
                it.remove();
            }
        }

        if (jobsOutput.length() > 0) {
            write(currentDir, outFile, jobsOutput.toString().trim(), false);
        }
    }

    private static void executeCdBuiltin(List<String> cmdTokens, String errFile, String currentDir) throws Exception {
        String path = cmdTokens.size() > 1 ? cmdTokens.get(1) : "";
        if (path.equals("~")) path = System.getenv("HOME");

        File f = new File(path);
        if (!f.isAbsolute()) f = new File(currentDir, path);

        try {
            String canon = f.getCanonicalPath();
            File real = new File(canon);
            if (real.exists() && real.isDirectory()) {
                System.setProperty("user.dir", canon);
            } else {
                write(currentDir, errFile, "cd: " + path + ": No such file or directory", true);
            }
        } catch (Exception e) {
            write(currentDir, errFile, "cd: " + path + ": No such file or directory", true);
        }
    }

    private static void executeTypeBuiltin(List<String> cmdTokens, String outFile, String currentDir) throws Exception {
        String name = cmdTokens.get(1);
        if (name.equals("echo") || name.equals("pwd") || name.equals("cd") || name.equals("type") || name.equals("exit") || name.equals("jobs")) {
            write(currentDir, outFile, name + " is a shell builtin", false);
        } else {
            String[] paths = System.getenv("PATH").split(":");
            boolean found = false;
            for (String p : paths) {
                File f = new File(p, name);
                if (f.exists() && f.canExecute()) {
                    write(currentDir, outFile, name + " is " + f.getAbsolutePath(), false);
                    found = true;
                    break;
                }
            }
            if (!found) write(currentDir, outFile, name + ": not found", false);
        }
    }

    private static int getLowestAvailableJobId() {
        List<Integer> sortedIds = new ArrayList<>();
        for (BackgroundJob job : activeJobs) sortedIds.add(job.id);
        Collections.sort(sortedIds);
        int candidate = 1;
        for (int id : sortedIds) {
            if (id == candidate) candidate++;
            else if (id > candidate) break;
        }
        return candidate;
    }

    private static void reapCompletedJobs() {
        Iterator<BackgroundJob> it = activeJobs.iterator();
        List<BackgroundJob> finished = new ArrayList<>();
        while (it.hasNext()) {
            BackgroundJob job = it.next();
            if (!job.process.isAlive()) {
                finished.add(job);
                it.remove();
            }
        }
        int totalActive = activeJobs.size();
        for (int i = 0; i < finished.size(); i++) {
            BackgroundJob job = finished.get(i);
            String sign = " ";
            int virtualIndex = totalActive + i;
            if (virtualIndex == totalActive + finished.size() - 1) sign = "+";
            else if (virtualIndex == totalActive + finished.size() - 2) sign = "-";
            System.out.println("[" + job.id + "]" + sign + "  Done                    " + job.commandStr);
            System.out.flush();
        }
    }

    private static void createOrPrepareFile(String dir, String file, boolean append) throws Exception {
        File targetFile = new File(file);
        if (!targetFile.isAbsolute()) targetFile = new File(dir, file);
        if (targetFile.getParentFile() != null) targetFile.getParentFile().mkdirs();
        if (!append) {
            new FileOutputStream(targetFile).close();
        } else {
            if (!targetFile.exists()) targetFile.createNewFile();
        }
    }

    private static void write(String dir, String file, String output, boolean isStderr) throws Exception {
        String formattedOutput = output + "\n";
        if (file != null) {
            File targetFile = new File(file);
            if (!targetFile.isAbsolute()) targetFile = new File(dir, file);
            FileOutputStream fos = new FileOutputStream(targetFile, true);
            fos.write(formattedOutput.getBytes());
            fos.close();
        } else {
            if (isStderr) System.err.print(formattedOutput);
            else System.out.print(formattedOutput);
        }
    }
}