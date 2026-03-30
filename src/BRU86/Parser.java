package BRU86;

import java.util.*;

/**
 * Parses 8086 assembly source code into a list of Instructions.
 * Supports labels, DB/DW directives, comments (;), EQU constants.
 */
public class Parser {
    private final Map<String, Integer> labels = new LinkedHashMap<>();
    private final Map<String, Integer> constants = new LinkedHashMap<>();
    private final List<Instruction> instructions = new ArrayList<>();
    // Data segments defined by DB/DW — stored as label -> address
    private final Map<String, Integer> dataAddresses = new LinkedHashMap<>();

    public static class ParseResult {
        public final List<Instruction> instructions;
        public final Map<String, Integer> labels;
        public final Map<String, Integer> constants;
        public final Map<String, Integer> dataAddresses;
        public final List<DataEntry> dataEntries;

        public ParseResult(List<Instruction> instructions, Map<String, Integer> labels,
                           Map<String, Integer> constants, Map<String, Integer> dataAddresses,
                           List<DataEntry> dataEntries) {
            this.instructions = instructions;
            this.labels = labels;
            this.constants = constants;
            this.dataAddresses = dataAddresses;
            this.dataEntries = dataEntries;
        }
    }

    public static class DataEntry {
        public final String label;
        public final boolean isWord; // true = DW, false = DB
        public final List<Integer> values;
        public int address; // assigned during second pass

        public DataEntry(String label, boolean isWord, List<Integer> values) {
            this.label = label;
            this.isWord = isWord;
            this.values = values;
        }
    }

    public ParseResult parse(String source) {
        String[] lines = source.split("\\r?\\n");
        List<DataEntry> dataEntries = new ArrayList<>();
        List<String[]> rawInstructions = new ArrayList<>(); // {label, mnemonic, operands_str, original, lineNum}

        boolean inDataSegment = false;
        boolean inCodeSegment = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // Strip comment
            int commentIdx = line.indexOf(';');
            if (commentIdx >= 0) line = line.substring(0, commentIdx).trim();
            if (line.isEmpty()) continue;

            // Handle segment directives
            if (line.equalsIgnoreCase(".DATA") || line.equalsIgnoreCase("SEGMENT DATA") || line.equalsIgnoreCase("DATA SEGMENT")) {
                inDataSegment = true; inCodeSegment = false; continue;
            }
            if (line.equalsIgnoreCase(".CODE") || line.equalsIgnoreCase("SEGMENT CODE") || line.equalsIgnoreCase("CODE SEGMENT")) {
                inDataSegment = false; inCodeSegment = true; continue;
            }
            if (line.equalsIgnoreCase("ENDS") || line.equalsIgnoreCase(".STACK")) {
                inDataSegment = false; continue;
            }
            if (line.equalsIgnoreCase("END") || line.toUpperCase().startsWith("END ")) continue;

            // Extract label (ends with :)
            String label = null;
            String rest = line;
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String potentialLabel = line.substring(0, colonIdx).trim();
                if (isValidIdentifier(potentialLabel)) {
                    label = potentialLabel.toUpperCase();
                    rest = line.substring(colonIdx + 1).trim();
                }
            }

            if (rest.isEmpty()) {
                // Just a label line — will be associated with next instruction
                rawInstructions.add(new String[]{label, null, null, line, String.valueOf(i + 1)});
                continue;
            }

            // Parse mnemonic and operands
            String[] parts = rest.split("\\s+", 2);
            String mnemonic = parts[0].toUpperCase();
            String operandsStr = parts.length > 1 ? parts[1].trim() : "";

            // Handle EQU
            if (operandsStr.toUpperCase().startsWith("EQU") || mnemonic.equals("EQU")) {
                // label EQU value  or  EQU name value
                if (label != null && mnemonic.equals("EQU")) {
                    try { constants.put(label, parseImmediate(operandsStr)); } catch (Exception e) {}
                } else if (parts.length >= 2) {
                    String[] equParts = rest.split("\\s+", 3);
                    if (equParts.length == 3 && equParts[1].equalsIgnoreCase("EQU")) {
                        try { constants.put(equParts[0].toUpperCase(), parseImmediate(equParts[2])); } catch (Exception e) {}
                    }
                }
                continue;
            }

            // Handle data directives DB/DW
            if (mnemonic.equals("DB") || mnemonic.equals("DW")) {
                boolean isWord = mnemonic.equals("DW");
                List<Integer> values = parseDataValues(operandsStr, isWord);
                DataEntry entry = new DataEntry(label, isWord, values);
                dataEntries.add(entry);
                continue;
            }

            // Store raw instruction for second pass
            rawInstructions.add(new String[]{label, mnemonic, operandsStr, line, String.valueOf(i + 1)});
        }

        // Second pass: build instructions list, resolve label positions
        // Data section starts at 0x0200, code at 0x0100
        int dataAddr = 0x0200;
        for (DataEntry entry : dataEntries) {
            entry.address = dataAddr;
            if (entry.label != null) {
                dataAddresses.put(entry.label.toUpperCase(), dataAddr);
                constants.put(entry.label.toUpperCase(), dataAddr);
            }
            dataAddr += entry.values.size() * (entry.isWord ? 2 : 1);
        }

        // Pending label for next instruction
        String pendingLabel = null;
        int instrIndex = 0;
        for (String[] raw : rawInstructions) {
            String lbl = raw[0];
            String mnemonic = raw[1];
            String operandsStr = raw[2];
            String origLine = raw[3];
            int lineNum = Integer.parseInt(raw[4]);

            if (lbl != null) pendingLabel = lbl;
            if (mnemonic == null) continue; // label-only line

            // Record label pointing to this instruction index
            if (pendingLabel != null) {
                labels.put(pendingLabel, instrIndex);
                pendingLabel = null;
            }

            List<String> ops = parseOperandList(operandsStr);
            instructions.add(new Instruction(lineNum, origLine, null, mnemonic, ops));
            instrIndex++;
        }

        return new ParseResult(instructions, labels, constants, dataAddresses, dataEntries);
    }

    private List<Integer> parseDataValues(String str, boolean isWord) {
        List<Integer> values = new ArrayList<>();
        // Handle string literals: "Hello", 0
        if (str.contains("\"") || str.contains("'")) {
            // Mixed: could be "Hi", 13, 10, '$'
            String[] parts = str.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("\"") || part.startsWith("'")) {
                    char quote = part.charAt(0);
                    int end = part.lastIndexOf(quote);
                    if (end > 0) {
                        String s = part.substring(1, end);
                        for (char c : s.toCharArray()) values.add((int) c);
                    }
                } else {
                    try { values.add(parseImmediate(part)); } catch (Exception e) { values.add(0); }
                }
            }
        } else {
            // Numeric list or DUP
            if (str.toUpperCase().contains("DUP")) {
                // e.g., 10 DUP(0)
                var dupMatcher = java.util.regex.Pattern.compile(
                    "(\\d+)\\s+DUP\\s*\\(([^)]+)\\)", java.util.regex.Pattern.CASE_INSENSITIVE
                ).matcher(str);
                if (dupMatcher.find()) {
                    int count = Integer.parseInt(dupMatcher.group(1));
                    int val = parseImmediate(dupMatcher.group(2).trim());
                    for (int i = 0; i < count; i++) values.add(val);
                }
            } else {
                String[] parts = str.split(",");
                for (String part : parts) {
                    try { values.add(parseImmediate(part.trim())); } catch (Exception e) { values.add(0); }
                }
            }
        }
        if (values.isEmpty()) values.add(0);
        return values;
    }

    private List<String> parseOperandList(String operandsStr) {
        List<String> ops = new ArrayList<>();
        if (operandsStr == null || operandsStr.isEmpty()) return ops;

        // Split by comma, but respect brackets []
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : operandsStr.toCharArray()) {
            if (c == '[') depth++;
            if (c == ']') depth--;
            if (c == ',' && depth == 0) {
                ops.add(cur.toString().trim());
                cur = new StringBuilder();
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) ops.add(cur.toString().trim());
        return ops;
    }

    public static int parseImmediate(String s) {
        s = s.trim();
        if (s.isEmpty()) throw new SimulatorException("Empty immediate");
        // Hex: 0FFh or 0xFF or 0FFH
        if (s.toUpperCase().endsWith("H")) {
            return Integer.parseInt(s.substring(0, s.length() - 1), 16);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Integer.parseInt(s.substring(2), 16);
        }
        // Binary: 1010b
        if (s.toUpperCase().endsWith("B") && s.substring(0, s.length()-1).matches("[01]+")) {
            return Integer.parseInt(s.substring(0, s.length()-1), 2);
        }
        // Char: 'A'
        if (s.startsWith("'") && s.endsWith("'") && s.length() == 3) {
            return s.charAt(1);
        }
        return Integer.parseInt(s);
    }

    private boolean isValidIdentifier(String s) {
        return s.matches("[A-Za-z_@][A-Za-z0-9_@]*");
    }
}
