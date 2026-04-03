package me.bintanq.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public final class ConfigUpdater {

    private ConfigUpdater() {}
    public static void update(JavaPlugin plugin, String fileName) throws IOException {
        File userFile = new File(plugin.getDataFolder(), fileName);

        if (!userFile.exists()) {
            plugin.saveResource(fileName, false);
            return;
        }

        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream == null) {
            plugin.getLogger().warning("[VEventHandler] No bundled resource found for: " + fileName);
            return;
        }

        List<String> defaultLines = readLines(defaultStream);
        List<String> userLines    = readLines(new FileInputStream(userFile));

        LinkedHashMap<String, RawEntry> defaultMap = parse(defaultLines);
        LinkedHashMap<String, RawEntry> userMap     = parse(userLines);

        List<String> output = merge(defaultMap, userMap);

        Files.write(userFile.toPath(), output, StandardCharsets.UTF_8);

        Set<String> added   = new LinkedHashSet<>(defaultMap.keySet());
        added.removeAll(userMap.keySet());

        Set<String> removed = new LinkedHashSet<>(userMap.keySet());
        removed.removeAll(defaultMap.keySet());

        if (!added.isEmpty())
            plugin.getLogger().info("[VEventHandler] " + fileName + " — added keys: " + added);
        if (!removed.isEmpty())
            plugin.getLogger().info("[VEventHandler] " + fileName + " — removed obsolete keys: " + removed);
    }

    private static class RawEntry {
        final List<String> headerLines;
        final String       keyLine;
        final List<String> bodyLines;
        final int          indent;

        RawEntry(List<String> headerLines, String keyLine, List<String> bodyLines, int indent) {
            this.headerLines = headerLines;
            this.keyLine     = keyLine;
            this.bodyLines   = bodyLines;
            this.indent      = indent;
        }
    }

    private static LinkedHashMap<String, RawEntry> parse(List<String> lines) {
        LinkedHashMap<String, RawEntry> map = new LinkedHashMap<>();

        Deque<int[]>  indentStack = new ArrayDeque<>();  // [0]=indent
        Deque<String> keyStack    = new ArrayDeque<>();

        List<String> pendingHeader = new ArrayList<>();
        int i = 0;

        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.isBlank() || isComment(line)) {
                pendingHeader.add(line);
                i++;
                continue;
            }

            if (line.startsWith("---") || line.startsWith("...")) {
                pendingHeader.clear();
                i++;
                continue;
            }

            if (line.stripLeading().startsWith("- ") || line.strip().equals("-")) {
                pendingHeader.clear();
                i++;
                continue;
            }

            int indent = leadingSpaces(line);
            String stripped = line.stripLeading();

            int colonIdx = colonIndex(stripped);
            if (colonIdx < 0) {
                pendingHeader.clear();
                i++;
                continue;
            }

            String keySegment = stripped.substring(0, colonIdx).trim();
            String rest       = stripped.substring(colonIdx + 1);

            while (!indentStack.isEmpty() && indentStack.peek()[0] >= indent) {
                indentStack.pop();
                keyStack.pop();
            }

            List<String> pathParts = new ArrayList<>(keyStack);
            pathParts.add(keySegment);
            String fullPath = String.join(".", pathParts);

            List<String> body = new ArrayList<>();
            String trimmedRest = rest.trim();

            boolean isBlockScalar = trimmedRest.equals("|") || trimmedRest.equals(">")
                    || trimmedRest.equals("|-") || trimmedRest.equals(">-")
                    || trimmedRest.equals("|+") || trimmedRest.equals(">+");

            if (isBlockScalar) {
                int j = i + 1;
                while (j < lines.size()) {
                    String next = lines.get(j);
                    if (next.isBlank()) {
                        body.add(next);
                        j++;
                    } else if (leadingSpaces(next) > indent) {
                        body.add(next);
                        j++;
                    } else {
                        break;
                    }
                }
                i = j;
            } else if (trimmedRest.isEmpty()) {
                int j = i + 1;
                while (j < lines.size()) {
                    String next = lines.get(j);
                    if (next.isBlank()) { j++; continue; }
                    String nextStripped = next.stripLeading();
                    if (nextStripped.startsWith("- ") || nextStripped.equals("-")) {
                        while (j < lines.size()) {
                            String item = lines.get(j);
                            if (item.isBlank()) { j++; continue; }
                            if (leadingSpaces(item) > indent ||
                                    item.stripLeading().startsWith("- ") ||
                                    item.strip().equals("-")) {
                                body.add(item);
                                j++;
                            } else {
                                break;
                            }
                        }
                        i = j;
                    }
                    break;
                }
                if (body.isEmpty()) {
                    i++;
                }
            } else {
                i++;
            }

            indentStack.push(new int[]{indent});
            keyStack.push(keySegment);

            RawEntry entry = new RawEntry(
                    new ArrayList<>(pendingHeader),
                    line,
                    body,
                    indent
            );
            map.put(fullPath, entry);
            pendingHeader = new ArrayList<>();
        }

        return map;
    }

    private static List<String> merge(
            LinkedHashMap<String, RawEntry> defaultMap,
            LinkedHashMap<String, RawEntry> userMap) {

        List<String> out = new ArrayList<>();

        for (Map.Entry<String, RawEntry> e : defaultMap.entrySet()) {
            String    path         = e.getKey();
            RawEntry  defaultEntry = e.getValue();
            RawEntry  userEntry    = userMap.get(path);

            out.addAll(defaultEntry.headerLines);

            if (userEntry != null && hasScalarOrList(userEntry)) {
                out.add(userEntry.keyLine);
                out.addAll(userEntry.bodyLines);
            } else {
                out.add(defaultEntry.keyLine);
                out.addAll(defaultEntry.bodyLines);
            }
        }

        return out;
    }

    private static boolean hasScalarOrList(RawEntry entry) {
        String rest = afterColon(entry.keyLine);
        if (rest != null && !rest.isBlank()) return true;
        return !entry.bodyLines.isEmpty();
    }

    private static String afterColon(String line) {
        String stripped = line.stripLeading();
        int idx = colonIndex(stripped);
        if (idx < 0) return null;
        return stripped.substring(idx + 1);
    }

    private static int colonIndex(String s) {
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == ':' && !inSingle && !inDouble) {
                if (i + 1 >= s.length() || s.charAt(i + 1) == ' ' || s.charAt(i + 1) == '\t') {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isComment(String line) {
        return line.stripLeading().startsWith("#");
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else if (c == '\t') count += 2;
            else break;
        }
        return count;
    }

    private static List<String> readLines(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.toList());
        }
    }
}