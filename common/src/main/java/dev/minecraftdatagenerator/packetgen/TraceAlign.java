package dev.minecraftdatagenerator.packetgen;

import java.util.*;

/**
 * Pairs each vanilla write op with the reads vanilla's decoder made over the same bytes. The
 * read kind is what establishes signedness ({@code readByte} vs {@code readUnsignedByte}); a
 * var-int read appears as a run of single-byte reads and a string payload as a skip.
 * <p>
 * When the reads do not line up with the writes (one read spanning two writes, or a fixed write
 * read in parts) the sample is marked misaligned and keeps its own read partition.
 */
final class TraceAlign {
    static final Map<String, Integer> FIXED_SIZE = Map.ofEntries(
            Map.entry("bool", 1), Map.entry("i8", 1), Map.entry("u8", 1),
            Map.entry("i16", 2), Map.entry("u16", 2), Map.entry("li16", 2), Map.entry("lu16", 2), Map.entry("char", 2),
            Map.entry("i24", 3), Map.entry("u24", 3), Map.entry("li24", 3), Map.entry("lu24", 3),
            Map.entry("i32", 4), Map.entry("u32", 4), Map.entry("li32", 4), Map.entry("lu32", 4), Map.entry("f32", 4),
            Map.entry("i64", 8), Map.entry("li64", 8), Map.entry("f64", 8), Map.entry("blockpos", 8));

    record Result(String[] readKinds, boolean misaligned, boolean readComplete) {}

    static Result align(List<TraceOp> writes, List<TraceOp> reads, int bodyLength, boolean rewound) {
        String[] kinds = new String[writes.size()];
        boolean misaligned = rewound;
        int r = 0, cursor = 0;
        boolean contiguous = !rewound;
        for (TraceOp read : reads) {
            if (read.off() != cursor) contiguous = false;
            cursor = read.end();
        }
        boolean readComplete = contiguous && cursor == bodyLength;
        for (int w = 0; w < writes.size(); w++) {
            TraceOp write = writes.get(w);
            if (write.len() == 0) { kinds[w] = "none"; continue; }
            // Reads wholly before this write belong to earlier writes.
            while (r < reads.size() && reads.get(r).end() <= write.off()) r++;
            List<TraceOp> inside = new ArrayList<>();
            int k = r;
            while (k < reads.size() && reads.get(k).off() < write.end()) {
                TraceOp read = reads.get(k);
                if (read.len() == 0) { k++; continue; }
                inside.add(read);
                if (read.off() < write.off() || read.end() > write.end()) { misaligned = true; break; }
                k++;
            }
            // A read straddling into the next write stays current for that write too.
            r = k;
            String kind = classify(write, inside);
            if (kind.equals("mixed")) misaligned = true;
            kinds[w] = kind;
        }
        return new Result(kinds, misaligned, readComplete);
    }

    private static String classify(TraceOp write, List<TraceOp> reads) {
        if (reads.isEmpty()) return "none";
        boolean exact = reads.get(0).off() == write.off() && reads.get(reads.size() - 1).end() == write.end();
        if (!exact) return "mixed";
        switch (write.kind()) {
            case "varint", "varlong" -> {
                if (reads.stream().allMatch(op -> op.kind().equals("i8") && op.len() == 1)) return write.kind();
                if (reads.size() == 1) return reads.get(0).kind();
                return "mixed";
            }
            case "string" -> {
                // var-int length bytes, then the payload consumed by one bulk read or index move
                int prefix = 0;
                while (prefix < reads.size() && reads.get(prefix).kind().equals("i8") && reads.get(prefix).len() == 1) prefix++;
                if (prefix == 0) return "mixed";
                if (prefix == reads.size()) return "string";
                if (prefix == reads.size() - 1 && Set.of("skip", "bytes").contains(reads.get(prefix).kind())) return "string";
                return "mixed";
            }
            case "bytes" -> {
                if (reads.stream().allMatch(op -> op.kind().equals("bytes") || op.kind().equals("skip"))) return "bytes";
                if (reads.size() == 1) return reads.get(0).kind();
                return "mixed";
            }
            default -> {
                return reads.size() == 1 ? reads.get(0).kind() : "mixed";
            }
        }
    }
}
