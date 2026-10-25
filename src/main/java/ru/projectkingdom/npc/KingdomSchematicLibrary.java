package ru.projectkingdom.npc;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Reads WorldEdit Sponge .schem v2/v3 block palettes without WorldEdit runtime.
 *
 * Place .schem files in <world>/data/kingdom_schematics/<name>.schem.
 * Creating a project freezes the file to a content-addressed snapshot, so an
 * administrator changing the source blueprint cannot silently corrupt progress.
 * No block entity NBT, entities, biomes, inventories, or commands are imported.
 */
public final class KingdomSchematicLibrary {
    private KingdomSchematicLibrary() {}

    private static final String PREFIX = "schem:";
    private static final int MAX_EDGE = 48;
    private static final int MAX_VOLUME = 65_536;
    private static final long MAX_COMPRESSED = 2L * 1024L * 1024L;
    private static final String HASH_PATTERN = "[a-f0-9]{64}";
    /**
     * These three schematics are original Project Kingdom assets, shipped with
     * the mod JAR. Imported third-party schematics remain world-local.
     */
    private static final List<String> BUNDLED_AVALON = List.of(
        "avalon_merchant_house", "avalon_royal_gate", "avalon_tfc_smithy_shell");

    private static final Map<MinecraftServer, Map<String, KingdomDesignEngine.Design>> CACHE =
        new WeakHashMap<>();

    public static boolean isKey(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    public static String title(String key) {
        if (!isKey(key)) return key;
        String[] parts = key.split(":", -1);
        return parts.length == 3 ? parts[1] : key;
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[a-z0-9_-]{1,48}");
    }

    private static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
            .resolve("data").resolve("kingdom_schematics");
    }

    /**
     * Unpacks first-party plans once per world. Never overwrites an existing
     * file, even if the admin has already modified the building in their world.
     * Import happens only in server-side code: no client filesystem access.
     */
    private static void installBundled(MinecraftServer server) {
        Path directory = folder(server);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.createDirectories(directory);
            for (String name : BUNDLED_AVALON) {
                Path target = directory.resolve(name + ".schem");
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
                String resource = "/assets/kingdomnpc/avalon/" + name + ".schem";
                try (var input = KingdomSchematicLibrary.class.getResourceAsStream(resource)) {
                    if (input == null) throw new IOException("Missing bundled plan: " + name);
                    Path temp = Files.createTempFile(directory, name + "-", ".tmp");
                    try {
                        Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
                        readDesign(temp, name); // Reject invalid or unsafe blocks before installation.
                        try {
                            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                            Files.move(temp, target);
                        } catch (java.nio.file.FileAlreadyExistsException ignored) {
                            // World administrator created their own file concurrently.
                        }
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("[Project Kingdom] Avalon schematic import failed: " + error.getMessage());
        }
    }

    public static List<String> available(MinecraftServer server) {
        installBundled(server);
        Path folder = folder(server);
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) return List.of();
        ArrayList<String> names = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.schem")) {
            for (Path file : files) {
                String filename = file.getFileName().toString();
                String name = filename.substring(0, filename.length() - 6);
                if (validName(name) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && Files.size(file) <= MAX_COMPRESSED)
                    names.add(name);
            }
        } catch (IOException ignored) { return List.of(); }
        names.sort(String::compareTo);
        return List.copyOf(names.subList(0, Math.min(64, names.size())));
    }

    /**
     * Validates source, then freezes its exact content. Returns a persistent key.
     * This is called only after the player has a blueprint and an eligible site.
     */
    public static String freeze(MinecraftServer server, String name) throws IOException {
        if (!validName(name)) throw new IOException("Invalid schematic name");
        // Backstop for callers who open a plan directly rather than via the list.
        if (BUNDLED_AVALON.contains(name)) installBundled(server);
        Path source = folder(server).resolve(name + ".schem");
        validateFile(source);
        // Validate the complete blockstate palette before making it selectable.
        readDesign(source, name);
        byte[] sourceBytes = Files.readAllBytes(source);
        String hash = hexSha256(sourceBytes);
        Path snapshots = folder(server).resolve(".snapshots");
        Files.createDirectories(snapshots);
        Path target = snapshots.resolve(hash + ".schem");
        if (!Files.exists(target)) {
            Path temporary = Files.createTempFile(snapshots, "schematic-", ".tmp");
            try {
                Files.write(temporary, sourceBytes);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // Another import won the race: validate the exact same hash below.
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        validateFile(target);
        if (!hash.equals(hexSha256(Files.readAllBytes(target))))
            throw new IOException("Frozen schematic was modified");
        readDesign(target, name);
        return PREFIX + name + ":" + hash;
    }

    public static KingdomDesignEngine.Design resolve(MinecraftServer server, String key) {
        if (!isKey(key)) return null;
        String[] parts = key.split(":", -1);
        if (parts.length != 3 || !validName(parts[1]) || !parts[2].matches(HASH_PATTERN))
            return null;
        synchronized (CACHE) {
            Map<String, KingdomDesignEngine.Design> cache =
                CACHE.computeIfAbsent(server, ignored -> new HashMap<>());
            var existing = cache.get(key);
            if (existing != null) return existing;
            Path source = folder(server).resolve(".snapshots").resolve(parts[2] + ".schem");
            try {
                validateFile(source);
                if (!parts[2].equals(hexSha256(Files.readAllBytes(source)))) return null;
                var design = readDesign(source, parts[1]);
                cache.put(key, design);
                return design;
            } catch (IOException | IllegalArgumentException ex) {
                return null;
            }
        }
    }

    public static KingdomDesignEngine.Design preview(MinecraftServer server, String name)
            throws IOException {
        if (!validName(name)) throw new IOException("Invalid schematic name");
        return readDesign(folder(server).resolve(name + ".schem"), name);
    }

    private static void validateFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Schematic not found: " + path.getFileName());
        long size = Files.size(path);
        if (size < 30 || size > MAX_COMPRESSED)
            throw new IOException("Schematic exceeds file size limit");
    }

    private static String hexSha256(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 unavailable", ex);
        }
    }

    static KingdomDesignEngine.Design readDesign(Path path, String name)
            throws IOException {
        validateFile(path);
        // Bounded NBT read protects the server from compressed NBT expansion.
        CompoundTag root;
        try (var compressed = new GZIPInputStream(Files.newInputStream(path));
             var input = new DataInputStream(compressed)) {
            root = NbtIo.read(input, new NbtAccounter(12L * 1024L * 1024L));
        } catch (RuntimeException ex) {
            throw new IOException("Invalid schematic NBT: " + ex.getMessage(), ex);
        }
        if (root.contains("Schematic", Tag.TAG_COMPOUND))
            root = root.getCompound("Schematic");
        int version = root.getInt("Version");
        if (version != 2 && version != 3)
            throw new IOException("Only Sponge schematic v2/v3 is supported (got " + version + ")");
        int width = root.getInt("Width"), height = root.getInt("Height");
        int length = root.getInt("Length");
        if (width < 1 || height < 1 || length < 1 || width > MAX_EDGE
                || height > MAX_EDGE || length > MAX_EDGE
                || (long) width * height * length > MAX_VOLUME)
            throw new IOException("Schematic dimensions exceed 48x48x48 or 65,536 blocks");
        CompoundTag blocks = version == 3 ? root.getCompound("Blocks") : root;
        String dataName = version == 3 ? "Data" : "BlockData";
        if (!blocks.contains("Palette", Tag.TAG_COMPOUND)
                || !blocks.contains(dataName, Tag.TAG_BYTE_ARRAY))
            throw new IOException("Schematic has no block palette or varint data");
        CompoundTag palette = blocks.getCompound("Palette");
        if (palette.isEmpty() || palette.getAllKeys().size() > 4096)
            throw new IOException("Unsupported schematic palette");
        var states = new HashMap<Integer, String>();
        for (String state : palette.getAllKeys()) {
            int paletteId = palette.getInt(state);
            if (paletteId < 0 || paletteId > 4096 || states.put(paletteId, state) != null)
                throw new IOException("Invalid schematic palette index");
            // Reject unknown blocks at import instead of quietly building air.
            KingdomBlockStateCodec.parse(state);
        }
        byte[] stream = blocks.getByteArray(dataName);
        if (stream.length == 0 || stream.length > MAX_VOLUME * 5)
            throw new IOException("Invalid schematic block data length");
        int cursor = 0, volume = width * height * length;
        var cells = new ArrayList<KingdomDesignEngine.Cell>();
        for (int index = 0; index < volume; index++) {
            int value = 0, shift = 0, encoded;
            do {
                if (cursor >= stream.length || shift >= 35)
                    throw new IOException("Truncated or overflowing schematic varint");
                encoded = stream[cursor++] & 0xff;
                value |= (encoded & 127) << shift;
                shift += 7;
            } while ((encoded & 128) != 0);
            String spec = states.get(value);
            if (spec == null) throw new IOException("Unmapped schematic palette index " + value);
            var state = KingdomBlockStateCodec.parse(spec);
            if (state.isAir()) continue; // Never demolish existing terrain to match schematic air.
            if (!KingdomBlockStateCodec.buildable(state))
                throw new IOException("Unsupported survival block state: " + spec);
            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);
            cells.add(new KingdomDesignEngine.Cell(x, y, z, spec));
        }
        if (cursor != stream.length || cells.isEmpty())
            throw new IOException("Schematic block count invalid or entirely air");
        return new KingdomDesignEngine.Design(name, width, length, height, cells);
    }
}
